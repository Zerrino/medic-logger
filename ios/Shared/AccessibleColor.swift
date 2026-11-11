import SwiftUI
import UIKit

enum AccessibleColor {
    static func components(_ hex: String) -> [Double] {
        let n = UInt32(hex.trimmingCharacters(in:CharacterSet(charactersIn:"#")),radix:16) ?? 0xB63840
        return [Double((n >> 16)&255)/255,Double((n >> 8)&255)/255,Double(n&255)/255]
    }
    static func luminance(_ rgb: [Double]) -> Double {
        let c = rgb.map { $0 <= 0.04045 ? $0/12.92 : pow(($0+0.055)/1.055,2.4) }
        return c[0]*0.2126 + c[1]*0.7152 + c[2]*0.0722
    }
    static func contrast(_ a: [Double],_ b: [Double]) -> Double { let x = luminance(a),y = luminance(b);return (max(x,y)+0.05)/(min(x,y)+0.05) }
    static func adjusted(_ hex: String, background: [Double], minimum: Double) -> [Double] {
        let source = components(hex),end: Double = luminance(background) < 0.5 ? 1 : 0
        for step in 0...100 {
            let fraction = Double(step)/100,candidate = source.map { $0*(1-fraction) + end*fraction }
            if contrast(candidate,background) >= minimum { return candidate }
        }
        return [end,end,end]
    }
    static func ink(_ hex: String) -> Color {
        Color(uiColor:UIColor { traits in
            let surface = UIColor.secondarySystemGroupedBackground.resolvedColor(with:traits)
            var r:CGFloat=0,g:CGFloat=0,b:CGFloat=0,a:CGFloat=0;surface.getRed(&r,green:&g,blue:&b,alpha:&a)
            let c = adjusted(hex,background:[Double(r),Double(g),Double(b)],minimum:traits.accessibilityContrast == .high ? 7 : 5)
            return UIColor(red:CGFloat(c[0]),green:CGFloat(c[1]),blue:CGFloat(c[2]),alpha:1)
        })
    }
    static func onFill(_ hex: String) -> Color { luminance(components(hex)) > 0.179 ? .black : .white }
}
