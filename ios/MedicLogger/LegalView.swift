import SwiftUI

struct LegalView: View {
    let store: MedicStore
    let medical: Bool
    private var sections: [LegalContent.Section] { medical ? LegalContent.medical : LegalContent.privacy }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ForEach(sections) { section in
                    VStack(alignment: .leading, spacing: 7) {
                        Text(store.t(section.title)).font(.headline).accessibilityAddTraits(.isHeader)
                        Text(store.t(section.body)).font(.body).fixedSize(horizontal: false, vertical: true)
                    }
                }
                if !medical {
                    VStack(alignment: .leading, spacing: 7) {
                        Text(store.t("Publisher and contact")).font(.headline).accessibilityAddTraits(.isHeader)
                        Text(LegalContent.publisher)
                        if let email = URL(string: "mailto:" + LegalContent.email) {
                            Link(LegalContent.email, destination: email).accessibilityIdentifier("privacy-contact")
                        }
                        if let address = LegalContent.privacyURL, let url = URL(string: address) {
                            Link(store.t("View online"), destination: url)
                        }
                        Text(store.t("Last updated") + ": " + LegalContent.updated).font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: 680, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity)
            .textSelection(.enabled)
        }
        .navigationTitle(store.t(medical ? "Medical notice" : "Privacy policy"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
