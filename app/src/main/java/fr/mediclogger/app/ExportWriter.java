package fr.mediclogger.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import fr.mediclogger.app.Models.Event;
import fr.mediclogger.app.Models.ExportData;
import fr.mediclogger.app.Models.Session;

/** Offline export formats, using only the Java standard library. Never treats user text as a formula. */
public final class ExportWriter {
    private ExportWriter() {}
    public enum Format {
        JSON("json", "application/json"), CSV("csv", "text/csv"),
        EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        ALL("zip", "application/zip");
        public final String extension, mime;
        Format(String extension,String mime) { this.extension=extension; this.mime=mime; }
    }
    public static void write(OutputStream out, ExportData data, Format format, boolean zip) throws IOException {
        if(zip || format==Format.ALL) {
            try(ZipOutputStream archive=new ZipOutputStream(out,StandardCharsets.UTF_8)) {
                Format[] formats = format==Format.ALL ? new Format[]{Format.JSON,Format.CSV,Format.EXCEL} : new Format[]{format};
                for(Format f:formats) {
                    archive.putNextEntry(new ZipEntry(data.baseName()+"."+f.extension));
                    // An XLSX is itself a ZIP. Buffer only this nested workbook to avoid closing the outer stream.
                    if(f==Format.EXCEL) {
                        ByteArrayOutputStream workbook=new ByteArrayOutputStream();
                        xlsx(workbook,data); workbook.writeTo(archive);
                    } else raw(archive,data,f);
                    archive.closeEntry();
                }
            }
        } else raw(out,data,format);
    }
    private static void raw(OutputStream out,ExportData data,Format format) throws IOException {
        switch(format) {
            case JSON -> json(out,data);
            case CSV -> csv(out,data);
            case EXCEL -> xlsx(out,data);
            default -> throw new IllegalArgumentException("Choose an export format.");
        }
    }
    private static void json(OutputStream out,ExportData d) throws IOException {
        Writer w=new OutputStreamWriter(out,StandardCharsets.UTF_8);
        w.write("{\n  \"schema_version\": 3,\n  \"app\": \"Medic Logger\",\n  \"generated_at\": "+q(Models.iso(d.generatedAt))+",\n");
        w.write("  \"period\": {\"from\": "+q(d.from.toString())+", \"to\": "+q(d.to.toString())+", \"timezone\": "+q(d.zone.getId())+", \"selection\": \"event_timestamp\", \"inclusive_dates\": true},\n");
        w.write("  \"events\": [\n");
        for(int i=0;i<d.events.size();i++) {
            Event e=d.events.get(i); Session s=e.session;
            w.write("    {\"id\": "+q(e.id())+", \"session_id\": "+s.id+", \"medicine_id\": "+s.medicineId+", \"medicine\": "+q(s.medicineName)+", \"short_name\": "+q(s.alias)+", \"dose\": "+q(s.dose)+", \"type\": "+q(e.type)+", \"timestamp\": "+q(Models.iso(e.at))+", \"timestamp_local\": "+q(Models.local(e.at,d.zone))+", \"timestamp_ms\": "+e.at+", \"notes\": "+q(s.notes)+", \"monitor_effects\": "+s.monitorEffects+", \"stock_batch_id\": "+(s.stockBatchId==null?"null":s.stockBatchId)+", \"stock_units\": "+s.stockUnits+", \"effectiveness_rating\": "+(s.effectiveness==null?"null":s.effectiveness)+"}"+(i+1<d.events.size() ? "," : "")+"\n");
        }
        w.write("  ],\n  \"sessions\": [\n");
        for(int i=0;i<d.sessions.size();i++) {
            Session s=d.sessions.get(i);
            w.write("    {\"id\": "+s.id+", \"medicine_id\": "+s.medicineId+", \"medicine\": "+q(s.medicineName)+", \"short_name\": "+q(s.alias)+", \"dose\": "+q(s.dose)+", \"taken_at\": "+q(Models.iso(s.takenAt))+", \"effects_started_at\": "+nullableTime(s.onsetAt)+", \"effects_ended_at\": "+nullableTime(s.onsetAt==null ? null : s.endedAt)+", \"closed_without_effect_at\": "+nullableTime(s.onsetAt==null ? s.endedAt : null)+", \"status\": "+q(s.status())+", \"time_to_effect_seconds\": "+seconds(s.takenAt,s.onsetAt)+", \"effect_duration_seconds\": "+seconds(s.onsetAt,s.endedAt)+", \"recorded_timezone\": "+q(s.zoneId)+", \"notes\": "+q(s.notes)+", \"monitor_effects\": "+s.monitorEffects+", \"stock_batch_id\": "+(s.stockBatchId==null?"null":s.stockBatchId)+", \"stock_units\": "+s.stockUnits+", \"effectiveness_rating\": "+(s.effectiveness==null?"null":s.effectiveness)+"}"+(i+1<d.sessions.size() ? "," : "")+"\n");
        }
        w.write("  ]\n}\n"); w.flush();
    }
    private static String nullableTime(Long time) { return time==null ? "null" : q(Models.iso(time)); }
    private static String seconds(Long start,Long end) { return start==null || end==null ? "null" : Long.toString((end-start)/1000); }
    private static String q(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if(c<32) b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }
    private static final List<String> EVENT_HEADERS=Arrays.asList("event_id","session_id","medicine_id","medicine","short_name","dose","event","timestamp_utc","timestamp_local","export_timezone","recorded_timezone","notes","monitor_effects","stock_batch_id","stock_units","effectiveness_rating");
    private static List<String> eventRow(Event e,ExportData d) {
        Session s=e.session;
        return Arrays.asList(e.id(),""+s.id,""+s.medicineId,s.medicineName,s.alias,s.dose,e.type,Models.iso(e.at),Models.local(e.at,d.zone),d.zone.getId(),s.zoneId,s.notes,Boolean.toString(s.monitorEffects),s.stockBatchId==null?"":s.stockBatchId.toString(),Integer.toString(s.stockUnits),s.effectiveness==null?"":s.effectiveness.toString());
    }
    private static void csv(OutputStream out,ExportData d) throws IOException {
        Writer w=new OutputStreamWriter(out,StandardCharsets.UTF_8);
        w.write('\ufeff'); // Excel recognizes UTF-8 without mangling accents.
        csvRow(w,EVENT_HEADERS);
        for(Event e:d.events) csvRow(w,eventRow(e,d));
        w.flush();
    }
    private static void csvRow(Writer w,List<String> cells) throws IOException {
        for(int i=0;i<cells.size();i++) {
            if(i>0) w.write(',');
            String value=cells.get(i);
            String stripped=value.trim();
            if(!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0) value="'"+value;
            if(!value.isEmpty() && (value.charAt(0)=='\t' || value.charAt(0)=='\r')) value="'"+value;
            w.write('"'); w.write(value.replace("\"","\"\"")); w.write('"');
        }
        w.write("\r\n");
    }
    private static void xlsx(OutputStream out,ExportData d) throws IOException {
        try(ZipOutputStream z=new ZipOutputStream(out,StandardCharsets.UTF_8)) {
            entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><bookViews><workbookView/></bookViews><sheets><sheet name=\"Events\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Doses\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"About this export\" sheetId=\"3\" r:id=\"rId3\"/></sheets></workbook>");
            entry(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/><Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entry(z,"xl/styles.xml","<?xml version=\"1.0\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/><color rgb=\"FF302326\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/><color rgb=\"FFFFFFFF\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFB63840\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            List<List<String>> events=new ArrayList<>();
            for(Event e:d.events) events.add(eventRow(e,d));
            sheet(z,"xl/worksheets/sheet1.xml",EVENT_HEADERS,events,new int[]{1,2,13,14,15});
            List<String> doseHeaders=Arrays.asList("session_id","medicine_id","medicine","short_name","dose","status","taken_at_utc","effects_started_at_utc","effects_ended_at_utc","closed_without_effect_at_utc","time_to_effect_seconds","effect_duration_seconds","recorded_timezone","notes","monitor_effects","stock_batch_id","stock_units","effectiveness_rating");
            List<List<String>> doses=new ArrayList<>();
            for(Session s:d.sessions) doses.add(Arrays.asList(""+s.id,""+s.medicineId,s.medicineName,s.alias,s.dose,s.status(),Models.iso(s.takenAt),time(s.onsetAt),time(s.onsetAt==null ? null : s.endedAt),time(s.onsetAt==null ? s.endedAt : null),emptySeconds(s.takenAt,s.onsetAt),emptySeconds(s.onsetAt,s.endedAt),s.zoneId,s.notes,Boolean.toString(s.monitorEffects),s.stockBatchId==null?"":s.stockBatchId.toString(),Integer.toString(s.stockUnits),s.effectiveness==null?"":s.effectiveness.toString()));
            sheet(z,"xl/worksheets/sheet2.xml",doseHeaders,doses,new int[]{0,1,10,11,15,16,17});
            sheet(z,"xl/worksheets/sheet3.xml",Arrays.asList("Property","Value"),Arrays.asList(
                Arrays.asList("Application","Medic Logger"),Arrays.asList("Schema version","3"),
                Arrays.asList("Generated at (UTC)",Models.iso(d.generatedAt)),Arrays.asList("From (inclusive)",d.from.toString()),
                Arrays.asList("To (inclusive)",d.to.toString()),Arrays.asList("Date selection timezone",d.zone.getId()),
                Arrays.asList("Selection rule","Events are selected by their own timestamp. Doses contains full context for those events, including times outside the period."),
                Arrays.asList("Event count",""+d.events.size()),Arrays.asList("Dose count",""+d.sessions.size()),
                Arrays.asList("Duration unit","Seconds"),Arrays.asList("Privacy","Created locally. No server or account is used.")),new int[]{});
        }
    }
    private static String time(Long t) { return t==null ? "" : Models.iso(t); }
    private static String emptySeconds(Long start,Long end) { return start==null || end==null ? "" : seconds(start,end); }
    private static void sheet(ZipOutputStream z,String path,List<String> headers,List<List<String>> rows,int[] numeric) throws IOException {
        z.putNextEntry(new ZipEntry(path));
        Writer w=new OutputStreamWriter(z,StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1:"+column(headers.size()-1)+(rows.size()+1)+"\"/><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"18\"/><cols>");
        for(int i=0;i<headers.size();i++) w.write("<col min=\""+(i+1)+"\" max=\""+(i+1)+"\" width=\""+(headers.get(i).contains("notes") ? 50 : 25)+"\" customWidth=\"1\"/>");
        w.write("</cols><sheetData>");
        row(w,headers,1,true,new int[]{});
        for(int i=0;i<rows.size();i++) row(w,rows.get(i),i+2,false,numeric);
        w.write("</sheetData><autoFilter ref=\"A1:"+column(headers.size()-1)+(rows.size()+1)+"\"/></worksheet>");
        w.flush(); z.closeEntry();
    }
    private static void row(Writer w,List<String> cells,int index,boolean header,int[] numeric) throws IOException {
        w.write("<row r=\""+index+"\""+(header ? " ht=\"26\" customHeight=\"1\"" : "")+">");
        for(int i=0;i<cells.size();i++) {
            String v=cells.get(i), ref=column(i)+index;
            boolean number=false; for(int n:numeric) if(n==i && !v.isEmpty()) number=true;
            if(number) w.write("<c r=\""+ref+"\"><v>"+xml(v)+"</v></c>");
            else w.write("<c r=\""+ref+"\" t=\"inlineStr\" s=\""+(header?1:0)+"\"><is><t xml:space=\"preserve\">"+xml(v)+"</t></is></c>");
        }
        w.write("</row>");
    }
    private static String column(int i) {
        StringBuilder s=new StringBuilder(); do { s.insert(0,(char)('A'+i%26)); i=i/26-1; } while(i>=0); return s.toString();
    }
    private static String xml(String s) {
        StringBuilder b=new StringBuilder();
        s.codePoints().forEach(c->{
            if(c=='&') b.append("&amp;"); else if(c=='<') b.append("&lt;"); else if(c=='>') b.append("&gt;");
            else if(c=='"') b.append("&quot;"); else if(c=='\'') b.append("&apos;");
            else if(c==9 || c==10 || c==13 || (c>=0x20 && c<=0xd7ff) || (c>=0xe000 && c<=0xfffd) || (c>=0x10000 && c<=0x10ffff)) b.appendCodePoint(c);
        });
        return b.toString();
    }
    private static void entry(ZipOutputStream z,String path,String text) throws IOException {
        z.putNextEntry(new ZipEntry(path)); z.write(text.getBytes(StandardCharsets.UTF_8)); z.closeEntry();
    }
}
