import Foundation

enum Translations {
    private static let strings: [String:[String]] = {
        guard let url = Bundle.main.url(forResource:"Translations",withExtension:"json"), let data = try? Data(contentsOf:url),let values = try? JSONDecoder().decode([String:[String]].self,from:data) else { return [:] };return values
    }()
    static func text(_ key: String, language: String, arguments: [CVarArg] = []) -> String {
        var key = key
        let singular = ["%d doses":"%d dose","%d events":"%d event","%d reminders":"%d reminder","%d doses this month":"%d dose this month","%d saved medicines":"%d saved medicine","%d events selected":"%d event selected","%d doses are already active for this medicine. Log a separate dose?":"%d dose is already active for this medicine. Log a separate dose?"]
        if let count = arguments.first as? Int, count == 1, let one = singular[key] { key = one }
        let index = ["fr":0,"pt":1,"zh":2][language]
        let row = strings[key]
        let translated = index.flatMap { i in row.flatMap { $0.count > i ? $0[i] : nil } } ?? key
        guard !arguments.isEmpty else { return translated }
        let format = translated.replacingOccurrences(of:"%s",with:"%@").replacingOccurrences(of:"%d",with:"%lld")
        return String(format:format,locale:Locale(identifier:language),arguments:arguments)
    }
}
