package fr.mediclogger.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class Models {
    private Models() {}

    public static final class Medicine {
        public long id;
        public String name = "", alias = "", dose = "";
        public int color;
        public boolean favorite = true, archived;
        public boolean monitorEffects;
        public Long stockBatchId, stockRemaining;
        public String label() { return alias.trim().isEmpty() ? name : alias; }
    }

    public static final class Session {
        public long id, medicineId, takenAt;
        public Long onsetAt, endedAt;
        public String medicineName = "", alias = "", dose = "", notes = "", zoneId = "UTC";
        public int color;
        public boolean monitorEffects = true;
        public Long stockBatchId;
        public int stockUnits;
        public Integer effectiveness;
        public boolean active() { return monitorEffects && endedAt == null; }
        public String status() {
            if (!monitorEffects) return "taken";
            if (endedAt != null) return onsetAt == null ? "closed_without_effect" : "completed";
            return onsetAt == null ? "waiting_for_effect" : "effects_active";
        }
        public String label() { return alias.trim().isEmpty() ? medicineName : alias; }
        public void validate(long now) {
            if(effectiveness!=null&&(effectiveness<0||effectiveness>10||!monitorEffects||endedAt==null))
                throw new IllegalArgumentException("Rate a finished monitored dose from 0 to 10.");
            if (!monitorEffects && (onsetAt != null || endedAt != null))
                throw new IllegalArgumentException("Effect monitoring is disabled for this dose.");
            if (takenAt <= 0 || takenAt > now) throw new IllegalArgumentException("The dose time must be in the past or now.");
            if (onsetAt != null && (onsetAt < takenAt || onsetAt > now))
                throw new IllegalArgumentException("Effects must start after the dose, and cannot start in the future.");
            if (endedAt != null && (endedAt < (onsetAt == null ? takenAt : onsetAt) || endedAt > now))
                throw new IllegalArgumentException("The end time must follow the previous event, and cannot be in the future.");
        }
    }

    public static final class Event {
        public final Session session;
        public final String type;
        public final long at;
        Event(Session session, String type, long at) { this.session = session; this.type = type; this.at = at; }
        public String id() { return session.id + ":" + type; }
    }

    public static final class ExportData {
        public final LocalDate from, to;
        public final ZoneId zone;
        public final List<Session> sessions = new ArrayList<>();
        public final List<Event> events = new ArrayList<>();
        public final long generatedAt;

        public ExportData(List<Session> all, LocalDate from, LocalDate to, ZoneId zone, Long medicineId, long generatedAt) {
            if (to.isBefore(from)) throw new IllegalArgumentException("The end date must be on or after the start date.");
            this.from = from; this.to = to; this.zone = zone; this.generatedAt = generatedAt;
            long start = from.atStartOfDay(zone).toInstant().toEpochMilli();
            long end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            for (Session s : all) {
                if (medicineId != null && s.medicineId != medicineId) continue;
                int before = events.size();
                include(s, "dose_taken", s.takenAt, start, end);
                if (s.onsetAt != null) include(s, "effects_started", s.onsetAt, start, end);
                if (s.endedAt != null) include(s, s.onsetAt == null ? "closed_without_effect" : "effects_ended", s.endedAt, start, end);
                if (events.size() > before) sessions.add(s);
            }
            events.sort(Comparator.comparingLong((Event e) -> e.at).thenComparingLong(e -> e.session.id).thenComparing(e -> e.type));
        }
        private void include(Session s, String type, long at, long start, long end) {
            if (at >= start && at < end) events.add(new Event(s, type, at));
        }
        public String baseName() { return "medic-logger_" + from + "_to_" + to; }
        public static ExportData forMedicines(List<Session> all, LocalDate from, LocalDate to, ZoneId zone, Set<Long> medicineIds, long generatedAt) {
            if (medicineIds == null) return new ExportData(all,from,to,zone,null,generatedAt);
            List<Session> selected=new ArrayList<>();
            for(Session session:all)if(medicineIds.contains(session.medicineId))selected.add(session);
            return new ExportData(selected,from,to,zone,null,generatedAt);
        }
    }

    public static String iso(long at) { return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(at)); }
    public static String local(long at, ZoneId zone) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(at).atZone(zone));
    }
    public static String elapsed(long ms) {
        long minutes = Math.max(0, ms / 60000);
        if (minutes < 1) return "< 1 min";
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        return hours + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min");
    }
}
