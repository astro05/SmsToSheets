/**
 * Google Apps Script Web App bridge for the "SMS to Sheets" Android app.
 *
 * Setup / Updating:
 * 1. Open your Google Sheet -> Extensions -> Apps Script.
 * 2. Paste this entire file into Code.gs (replace existing code).
 * 3. Optional: Set SECRET_TOKEN below if you want to require a shared secret.
 * 4. Click Deploy -> Manage deployments -> Edit (pencil) -> Version: New version -> Deploy.
 *    - Execute as: Me
 *    - Who has access: Anyone
 * 5. Copy the Web App URL (ends with /exec) into the Android app.
 */

const SCRIPT_VERSION = "1.2.0";
const SECRET_TOKEN = ""; // Optional: e.g. "my-secret-123"
const DEFAULT_SHEET_NAME = "SMS_Logs";

const COLUMN_EXTRACTORS = {
  "Message ID": (row) => row.id || "",
  "Date / Time": (row) => row.timestamp || "",
  "Sender": (row) => row.sender || "",
  "Message Body": (row) => row.body || "",
  "Extracted Amount": (row) => row.extractedAmount || "",
  "Extracted Code / OTP": (row) => row.extractedCode || "",
  "Custom Extract": (row) => row.customExtract || "",
  "Rule Name": (row) => row.ruleName || "",
  "Sync Mode": (row) => row.source || "MANUAL",
  "Uploaded At": (row, uploadedAt) => uploadedAt
};

const DEFAULT_HEADERS = [
  "Message ID",
  "Date / Time",
  "Sender",
  "Message Body",
  "Extracted Amount",
  "Extracted Code / OTP",
  "Custom Extract",
  "Rule Name",
  "Sync Mode",
  "Uploaded At"
];

function doGet(e) {
  return jsonResponse({
    ok: true,
    service: "SMS to Sheets Bridge",
    version: SCRIPT_VERSION,
    timestamp: new Date().toISOString()
  });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(25000);

  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ ok: false, error: "Empty request body" });
    }

    const payload = JSON.parse(e.postData.contents);

    if (SECRET_TOKEN && payload.secret !== SECRET_TOKEN) {
      return jsonResponse({ ok: false, error: "Unauthorized: invalid secret token" });
    }

    const rows = Array.isArray(payload.rows) ? payload.rows : [];
    if (rows.length === 0) {
      return jsonResponse({ ok: true, inserted: 0, skipped: 0, message: "No rows provided" });
    }

    const spreadsheet = resolveSpreadsheet(payload.spreadsheetId);
    const sheetName = (payload.sheetName || DEFAULT_SHEET_NAME).toString().trim() || DEFAULT_SHEET_NAME;
    const sheet = getOrCreateSheet(spreadsheet, sheetName);

    const requestedHeaders = resolveRequestedHeaders(payload.columns);
    const activeHeaders = ensureHeaders(sheet, requestedHeaders);
    const requestedSet = new Set(requestedHeaders);

    const msgIdColIndex = activeHeaders.indexOf("Message ID");
    const existingIds = msgIdColIndex >= 0
      ? getExistingMessageIds(sheet, msgIdColIndex + 1)
      : new Set();

    const valuesToAppend = [];
    let skipped = 0;
    const uploadedAt = new Date().toISOString();

    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const msgId = (row.id || "").toString().trim();

      if (msgId && msgIdColIndex >= 0 && existingIds.has(msgId)) {
        skipped++;
        continue;
      }
      if (msgId) {
        existingIds.add(msgId);
      }

      const rowCells = activeHeaders.map((header) => {
        if (!requestedSet.has(header)) {
          return "";
        }
        const extractor = COLUMN_EXTRACTORS[header];
        return extractor ? extractor(row, uploadedAt) : "";
      });
      valuesToAppend.push(rowCells);
    }

    if (valuesToAppend.length > 0) {
      const startRow = sheet.getLastRow() + 1;
      sheet
        .getRange(startRow, 1, valuesToAppend.length, activeHeaders.length)
        .setValues(valuesToAppend);
    }

    return jsonResponse({
      ok: true,
      version: SCRIPT_VERSION,
      spreadsheetName: spreadsheet.getName(),
      sheetName: sheet.getName(),
      columns: activeHeaders,
      inserted: valuesToAppend.length,
      skipped: skipped
    });
  } catch (err) {
    return jsonResponse({
      ok: false,
      error: err && err.message ? err.message : String(err)
    });
  } finally {
    lock.releaseLock();
  }
}

function resolveRequestedHeaders(columnsPayload) {
  if (!Array.isArray(columnsPayload) || columnsPayload.length === 0) {
    return DEFAULT_HEADERS.slice();
  }
  const filtered = [];
  for (let i = 0; i < columnsPayload.length; i++) {
    const col = (columnsPayload[i] || "").toString().trim();
    if (col && COLUMN_EXTRACTORS[col] && filtered.indexOf(col) === -1) {
      filtered.push(col);
    }
  }
  return filtered.length > 0 ? filtered : DEFAULT_HEADERS.slice();
}

function resolveSpreadsheet(spreadsheetId) {
  const id = (spreadsheetId || "").toString().trim();
  if (id) {
    return SpreadsheetApp.openById(id);
  }
  const active = SpreadsheetApp.getActiveSpreadsheet();
  if (!active) {
    throw new Error("No active spreadsheet found. Either bind this script to a Google Sheet or pass spreadsheetId.");
  }
  return active;
}

function getOrCreateSheet(spreadsheet, sheetName) {
  let sheet = spreadsheet.getSheetByName(sheetName);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(sheetName);
  }
  return sheet;
}

function ensureHeaders(sheet, requestedHeaders) {
  // If sheet has no data rows yet (empty or header-only), set exact requested headers
  if (sheet.getLastRow() <= 1) {
    if (sheet.getLastRow() === 1) {
      sheet.getRange(1, 1, 1, Math.max(1, sheet.getLastColumn())).clearContent();
    }
    const headerRange = sheet.getRange(1, 1, 1, requestedHeaders.length);
    headerRange.setValues([requestedHeaders]);
    headerRange.setFontWeight("bold");
    headerRange.setBackground("#E8F0FE");
    sheet.setFrozenRows(1);
    return requestedHeaders.slice();
  }

  const lastCol = Math.max(1, sheet.getLastColumn());
  const existingHeaders = sheet
    .getRange(1, 1, 1, lastCol)
    .getValues()[0]
    .map((h) => (h || "").toString().trim());

  const missingHeaders = requestedHeaders.filter((h) => existingHeaders.indexOf(h) === -1);
  if (missingHeaders.length > 0) {
    const startCol = existingHeaders.length + 1;
    const addedRange = sheet.getRange(1, startCol, 1, missingHeaders.length);
    addedRange.setValues([missingHeaders]);
    addedRange.setFontWeight("bold");
    addedRange.setBackground("#E8F0FE");
    return existingHeaders.concat(missingHeaders);
  }

  return existingHeaders;
}

function getExistingMessageIds(sheet, oneBasedCol) {
  const ids = new Set();
  const lastRow = sheet.getLastRow();
  if (lastRow <= 1) {
    return ids;
  }
  const idValues = sheet.getRange(2, oneBasedCol, lastRow - 1, 1).getValues();
  for (let i = 0; i < idValues.length; i++) {
    const val = (idValues[i][0] || "").toString().trim();
    if (val) {
      ids.add(val);
    }
  }
  return ids;
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
