import ActivityKit
import Foundation

struct DoseActivity: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var name: String,phase: String,color: String,reference: Date,waiting: Bool,activeLabel: String,overLabel: String
    }
    var sessionID: Int64
}
