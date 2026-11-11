import Foundation
import SwiftUI
import UniformTypeIdentifiers

struct LogEvent: Identifiable {
    let dose: Dose, type: String, date: Date
    var id: String { "\(dose.id):\(type)" }
}
enum ExportFormat: String, CaseIterable, Identifiable {
    case json, csv, excel, all
    var id: String { rawValue }
    var label: String { switch self { case .json: "JSON"; case .csv: "CSV"; case .excel: "Excel (.xlsx)"; case .all: "All formats" } }
    var ext: String { switch self { case .json: "json"; case .csv: "csv"; case .excel: "xlsx"; case .all: "zip" } }
}
struct ExportSelection {
    let from: Date, to: Date, zone: TimeZone
    let events: [LogEvent], sessions: [Dose]
    let generatedAt: Date
    init(data: AppData, from: Date, to: Date, medicines: Set<Int64>? = nil, zone: TimeZone = .current, generatedAt: Date = Date()) throws {
        let cal = DayMath.calendar(zone), start = cal.startOfDay(for:from), last = cal.startOfDay(for:to)
        guard last >= start else { throw AppFailure("End date must be on or after start date.") }
        let end = cal.date(byAdding:.day,value:1,to:last)!
        self.from = start; self.to = last; self.zone = zone; self.generatedAt = generatedAt
        var rows: [LogEvent] = []
        for dose in data.doses where medicines == nil || medicines!.contains(dose.medicineID) {
            var times: [(String,Date)] = [("dose_taken",dose.takenAt)]
            if let onset = dose.onsetAt { times.append(("effects_started",onset)) }
            if let ended = dose.endedAt { times.append((dose.onsetAt == nil ? "closed_without_effect" : "effects_ended",ended)) }
            rows += times.filter { $0.1 >= start && $0.1 < end }.map { LogEvent(dose:dose,type:$0.0,date:$0.1) }
        }
        events = rows.sorted { $0.date != $1.date ? $0.date < $1.date : $0.dose.id != $1.dose.id ? $0.dose.id < $1.dose.id : $0.type < $1.type }
        let ids = Set(rows.map { $0.dose.id }); sessions = data.doses.filter { ids.contains($0.id) }.sorted { $0.takenAt < $1.takenAt }
    }
}
struct ExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents:data) }
}
enum ExportWriter {
    static let eventHeaders = ["event_id","session_id","medicine_id","medicine","short_name","dose","event","timestamp_utc","timestamp_local","export_timezone","recorded_timezone","notes","monitor_effects","stock_batch_id","stock_units","effectiveness_rating"]
    static let doseHeaders = ["session_id","medicine_id","medicine","short_name","dose","status","taken_at_utc","effects_started_at_utc","effects_ended_at_utc","closed_without_effect_at_utc","time_to_effect_seconds","effect_duration_seconds","recorded_timezone","notes","monitor_effects","stock_batch_id","stock_units","effectiveness_rating"]
    static func iso(_ date: Date?) -> String { guard let date else { return "" };let f = ISO8601DateFormatter();f.formatOptions = [.withInternetDateTime,.withFractionalSeconds];return f.string(from:date) }
    static func local(_ date: Date, zone: TimeZone) -> String { let f = ISO8601DateFormatter();f.timeZone = zone;f.formatOptions = [.withInternetDateTime,.withFractionalSeconds];return f.string(from:date) }
    static func seconds(_ a: Date?, _ b: Date?) -> String { guard let a,let b else { return "" };return String(Int64(b.timeIntervalSince(a))) }
    static func eventRow(_ e: LogEvent, _ selection: ExportSelection) -> [String] {
        let s = e.dose
        return [e.id,String(s.id),String(s.medicineID),s.medicineName,s.alias,s.dose,e.type,iso(e.date),local(e.date,zone:selection.zone),selection.zone.identifier,s.zoneID,s.notes,String(s.monitorEffects),s.stockBatchID.map(String.init) ?? "",String(s.stockUnits),s.effectiveness.map(String.init) ?? ""]
    }
    static func doseRow(_ s: Dose) -> [String] {
        [String(s.id),String(s.medicineID),s.medicineName,s.alias,s.dose,s.status,iso(s.takenAt),iso(s.onsetAt),iso(s.onsetAt == nil ? nil : s.endedAt),iso(s.onsetAt == nil ? s.endedAt : nil),seconds(s.takenAt,s.onsetAt),seconds(s.onsetAt,s.endedAt),s.zoneID,s.notes,String(s.monitorEffects),s.stockBatchID.map(String.init) ?? "",String(s.stockUnits),s.effectiveness.map(String.init) ?? ""]
    }
    static func write(_ s: ExportSelection, format: ExportFormat, zipped: Bool) throws -> Data {
        guard !s.events.isEmpty else { throw AppFailure("There are no events in this period.") }
        if zipped || format == .all {
            let formats: [ExportFormat] = format == .all ? [.json,.csv,.excel] : [format]
            let files = try formats.map { ("medic-logger." + $0.ext, try raw(s,format:$0)) };return try StoredZIP.write(files)
        }
        return try raw(s,format:format)
    }
    static func raw(_ s: ExportSelection, format: ExportFormat) throws -> Data {
        switch format {
        case .json: return try json(s)
        case .csv: return Data(("\u{feff}" + ([eventHeaders] + s.events.map { eventRow($0,s) }).map(csvRow).joined()).utf8)
        case .excel: return try WorkbookWriter.write(s)
        case .all: return try write(s,format:.all,zipped:true)
        }
    }
    static func json(_ s: ExportSelection) throws -> Data {
        let null = NSNull()
        let events: [[String:Any]] = s.events.map { e in
            let d = e.dose
            return ["id":e.id,"session_id":d.id,"medicine_id":d.medicineID,"medicine":d.medicineName,"short_name":d.alias,"dose":d.dose,"type":e.type,"timestamp":iso(e.date),"timestamp_local":local(e.date,zone:s.zone),"timestamp_ms":Int64(e.date.timeIntervalSince1970*1000),"notes":d.notes,"monitor_effects":d.monitorEffects,"stock_batch_id":d.stockBatchID.map { $0 as Any } ?? null,"stock_units":d.stockUnits,"effectiveness_rating":d.effectiveness.map { $0 as Any } ?? null]
        }
        let doses: [[String:Any]] = s.sessions.map { d in
            func time(_ value: Date?) -> Any { value.map { iso($0) as Any } ?? null }
            func duration(_ a: Date?,_ b: Date?) -> Any { guard let a,let b else { return null };return Int64(b.timeIntervalSince(a)) }
            return ["id":d.id,"medicine_id":d.medicineID,"medicine":d.medicineName,"short_name":d.alias,"dose":d.dose,"taken_at":iso(d.takenAt),"effects_started_at":time(d.onsetAt),"effects_ended_at":time(d.onsetAt == nil ? nil : d.endedAt),"closed_without_effect_at":time(d.onsetAt == nil ? d.endedAt : nil),"status":d.status,"time_to_effect_seconds":duration(d.takenAt,d.onsetAt),"effect_duration_seconds":duration(d.onsetAt,d.endedAt),"recorded_timezone":d.zoneID,"notes":d.notes,"monitor_effects":d.monitorEffects,"stock_batch_id":d.stockBatchID.map { $0 as Any } ?? null,"stock_units":d.stockUnits,"effectiveness_rating":d.effectiveness.map { $0 as Any } ?? null]
        }
        let root: [String:Any] = ["schema_version":3,"app":"Medic Logger","generated_at":iso(s.generatedAt),"period":["from":DayMath.dayString(s.from,zone:s.zone),"to":DayMath.dayString(s.to,zone:s.zone),"timezone":s.zone.identifier,"selection":"event_timestamp","inclusive_dates":true],"events":events,"sessions":doses]
        return try JSONSerialization.data(withJSONObject:root,options:[.prettyPrinted,.sortedKeys,.withoutEscapingSlashes])
    }
    static func csvRow(_ cells: [String]) -> String {
        cells.map { cell in
            var value = cell;let trimmed = value.trimmingCharacters(in:.whitespacesAndNewlines)
            if let first = trimmed.first, "=+-@".contains(first) { value = "'" + value }
            if value.first == "\t" || value.first == "\r" { value = "'" + value }
            return "\"" + value.replacingOccurrences(of:"\"",with:"\"\"") + "\""
        }.joined(separator:",") + "\r\n"
    }
}

enum StoredZIP {
    static func crc32(_ data: Data) -> UInt32 {
        var crc = UInt32.max
        for byte in data { crc ^= UInt32(byte);for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 1 ? 0xedb88320 : 0) } };return crc ^ .max
    }
    static func write(_ files: [(String,Data)]) throws -> Data {
        var output = Data(), central = Data()
        for (name,bytes) in files {
            let path = Data(name.utf8)
            guard bytes.count <= Int(UInt32.max), output.count <= Int(UInt32.max), path.count < 65536 else { throw AppFailure("The export is too large. Choose a shorter date range.") }
            let offset = UInt32(output.count),size = UInt32(bytes.count),crc = crc32(bytes)
            output.le32(0x04034b50);output.le16(20);output.le16(0x800);output.le16(0);output.le16(0);output.le16(0x21);output.le32(crc);output.le32(size);output.le32(size);output.le16(UInt16(path.count));output.le16(0);output += path;output += bytes
            central.le32(0x02014b50);central.le16(20);central.le16(20);central.le16(0x800);central.le16(0);central.le16(0);central.le16(0x21);central.le32(crc);central.le32(size);central.le32(size);central.le16(UInt16(path.count));central.le16(0);central.le16(0);central.le16(0);central.le16(0);central.le32(0);central.le32(offset);central += path
        }
        guard output.count <= Int(UInt32.max), central.count <= Int(UInt32.max),files.count < 65536 else { throw AppFailure("The export is too large. Choose a shorter date range.") }
        let offset = UInt32(output.count);output += central;output.le32(0x06054b50);output.le16(0);output.le16(0);output.le16(UInt16(files.count));output.le16(UInt16(files.count));output.le32(UInt32(central.count));output.le32(offset);output.le16(0);return output
    }
}
private extension Data {
    mutating func le16(_ value: UInt16) { append(UInt8(value & 255));append(UInt8((value >> 8) & 255)) }
    mutating func le32(_ value: UInt32) { le16(UInt16(value & 65535));le16(UInt16(value >> 16)) }
}

enum WorkbookWriter {
    static func write(_ selection: ExportSelection) throws -> Data {
        var files = metadata.map { ($0.key,Data($0.value.utf8)) }
        let about = [["Application","Medic Logger"],["Schema version","3"],["Generated at (UTC)",ExportWriter.iso(selection.generatedAt)],["From (inclusive)",DayMath.dayString(selection.from,zone:selection.zone)],["To (inclusive)",DayMath.dayString(selection.to,zone:selection.zone)],["Date selection timezone",selection.zone.identifier],["Selection rule","Events are selected by their own timestamp. Doses contains full context for those events, including times outside the period."],["Event count",String(selection.events.count)],["Dose count",String(selection.sessions.count)],["Duration unit","Seconds"]]
        files.append(("xl/worksheets/sheet1.xml",Data(sheet(ExportWriter.eventHeaders,selection.events.map { ExportWriter.eventRow($0,selection) },numeric:[1,2,13,14,15]).utf8)))
        files.append(("xl/worksheets/sheet2.xml",Data(sheet(ExportWriter.doseHeaders,selection.sessions.map(ExportWriter.doseRow),numeric:[0,1,10,11,15,16,17]).utf8)))
        files.append(("xl/worksheets/sheet3.xml",Data(sheet(["Property","Value"],about,numeric:[]).utf8)))
        return try StoredZIP.write(files.sorted { $0.0 < $1.0 })
    }
    static func column(_ input: Int) -> String { var value = input + 1,result = "";while value > 0 { value -= 1;result = String(UnicodeScalar(65 + value % 26)!) + result;value /= 26 };return result }
    static func escape(_ input: String) -> String {
        let clean = String(String.UnicodeScalarView(input.unicodeScalars.filter { $0.value == 9 || $0.value == 10 || $0.value == 13 || (0x20...0xd7ff).contains($0.value) || (0xe000...0xfffd).contains($0.value) || (0x10000...0x10ffff).contains($0.value) }))
        return clean.replacingOccurrences(of:"&",with:"&amp;").replacingOccurrences(of:"<",with:"&lt;").replacingOccurrences(of:">",with:"&gt;").replacingOccurrences(of:"\"",with:"&quot;")
    }
    static func sheet(_ headers: [String],_ rows: [[String]],numeric: Set<Int>) -> String {
        let last = column(headers.count-1) + String(rows.count+1)
        var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1:\(last)\"/><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>"
        for i in headers.indices { xml += "<col min=\"\(i+1)\" max=\"\(i+1)\" width=\"\(headers[i].contains("notes") ? 50 : 26)\" customWidth=\"1\"/>" };xml += "</cols><sheetData>"
        for (row,values) in ([headers] + rows).enumerated() {
            xml += "<row r=\"\(row+1)\">"
            for (col,value) in values.enumerated() {
                let reference = column(col) + String(row+1)
                if row > 0 && numeric.contains(col) && Int64(value) != nil { xml += "<c r=\"\(reference)\"><v>\(value)</v></c>" }
                else { xml += "<c r=\"\(reference)\" t=\"inlineStr\" s=\"\(row == 0 ? 1 : 0)\"><is><t xml:space=\"preserve\">\(escape(value))</t></is></c>" }
            };xml += "</row>"
        }
        return xml + "</sheetData><autoFilter ref=\"A1:\(last)\"/></worksheet>"
    }
    // OPC workbook metadata is generated from the Android writer to keep both exports identical.
    static let metadata: [String:String] = WorkbookMetadata.files
}
