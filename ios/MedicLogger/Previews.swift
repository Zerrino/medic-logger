#if DEBUG
import SwiftUI

enum PreviewFixtures {
    static var recorded: AppData {
        var data = AppData(),medicine = Medication();medicine.id = 1;medicine.name = "Example medicine";medicine.alias = "Morning";medicine.monitorEffects = true;medicine.color = "FFD700";data.medicines = [medicine]
        var dose = Dose();dose.id = 2;dose.medicineID = 1;dose.medicineName = medicine.name;dose.color = medicine.color;dose.takenAt = Date(timeIntervalSince1970:1793844000);data.doses = [dose];data.nextID = 3;return data
    }
}
#Preview("Today · Empty") { NavigationStack { TodayView(store:MedicStore(preview:AppData()),show:{ _ in }) }.tint(Theme.red) }
#Preview("Journal · Dark · Large Text") { NavigationStack { JournalView(store:MedicStore(preview:PreviewFixtures.recorded),show:{ _ in }) }.tint(Theme.red).preferredColorScheme(.dark).environment(\.dynamicTypeSize,.accessibility3) }
#endif
