import Foundation

/// Voice changer (port of Android AvraVoiceFx): processes 16-bit mono PCM in place. One instance
/// per recording. Presets: off, bass, helium, robot, echo, radio, demon, anonymous, chipmunk, giant,
/// plus an extra pitch in semitones. Settings live in UserDefaults (Avragram → Voice changer).
public final class AvraVoiceFx {
    public static let titles = ["Без эффекта", "Бас", "Хелий", "Робот", "Эхо", "Рация", "Демон", "Аноним", "Бурундук", "Великан"]
    public static let off = 0, bass = 1, helium = 2, robot = 3, echo = 4, radio = 5, demon = 6, anon = 7, chipmunk = 8, giant = 9

    public static var preset: Int {
        get { max(0, min(9, UserDefaults.standard.integer(forKey: "avra.voice.preset"))) }
        set { UserDefaults.standard.set(newValue, forKey: "avra.voice.preset") }
    }
    public static var pitch: Int {
        get { max(-12, min(12, UserDefaults.standard.integer(forKey: "avra.voice.pitch"))) }
        set { UserDefaults.standard.set(newValue, forKey: "avra.voice.pitch") }
    }

    private var sampleRate = 0
    private var currentPreset = -1
    private var semis = Int.min
    private var psBuf: [Float] = []
    private var psWrite = 0
    private var psPhase: Float = 0
    private var psRatio: Float = 1
    private var psWindow = 64
    private var dlBuf: [Float] = []
    private var dlWrite = 0
    private var ringPhase: Double = 0
    private var hpPrevIn: Float = 0, hpPrevOut: Float = 0, lp1: Float = 0, lp2: Float = 0

    public init() {}

    private static func presetPitch(_ p: Int) -> Int {
        switch p {
        case bass: return -5
        case helium: return 7
        case demon: return -9
        case anon: return -4
        case chipmunk: return 12
        case giant: return -12
        default: return 0
        }
    }

    /// Applies the current settings to `count` samples.
    public func process(_ samples: UnsafeMutablePointer<Int16>, count: Int, sampleRate sr: Int) {
        let p = AvraVoiceFx.preset, extra = AvraVoiceFx.pitch
        if p == AvraVoiceFx.off && extra == 0 { return }
        configure(sr, p, AvraVoiceFx.presetPitch(p) + extra)
        for i in 0..<count {
            var y = processSample(Float(samples[i]) / 32768)
            if y > 1 { y = 1 } else if y < -1 { y = -1 }
            samples[i] = Int16(y * 32767)
        }
    }

    private func configure(_ srIn: Int, _ p: Int, _ s: Int) {
        let sr = srIn > 0 ? srIn : 48000
        if sr == sampleRate && p == currentPreset && s == semis { return }
        let srChanged = sr != sampleRate
        sampleRate = sr
        currentPreset = p
        semis = s
        psRatio = Float(pow(2.0, Double(s) / 12.0))
        psWindow = max(64, Int(Float(sr) * 0.045))
        if srChanged || psBuf.count < psWindow * 2 + 4 {
            psBuf = [Float](repeating: 0, count: psWindow * 2 + 4)
            psWrite = 0
            psPhase = 0
            dlBuf = [Float](repeating: 0, count: sr)
            dlWrite = 0
            hpPrevIn = 0; hpPrevOut = 0; lp1 = 0; lp2 = 0
        }
    }

    private func processSample(_ x: Float) -> Float {
        var y = semis != 0 ? pitchShift(x) : x
        switch currentPreset {
        case AvraVoiceFx.robot:
            y = ring(y, 70, 1)
            y = comb(y, 0.006, 0.55, 1)
        case AvraVoiceFx.echo:
            y = comb(y, 0.22, 0.38, 0.5)
        case AvraVoiceFx.radio:
            y = radio(y)
        case AvraVoiceFx.demon:
            y = softClip(y * 1.8) * 0.8
            y = comb(y, 0.012, 0.3, 0.35)
        case AvraVoiceFx.anon:
            y = ring(y, 28, 0.35)
        default:
            break
        }
        return y
    }

    private func pitchShift(_ x: Float) -> Float {
        let n = psBuf.count
        psBuf[psWrite] = x
        let w = Float(psWindow)
        psPhase += 1 - psRatio
        while psPhase < 0 { psPhase += w }
        while psPhase >= w { psPhase -= w }
        let d1 = psPhase
        var d2 = psPhase + w * 0.5
        if d2 >= w { d2 -= w }
        var g1 = Float(sin(Double.pi * Double(d1 / w)))
        g1 *= g1
        let out = tap(d1 + 1, n) * g1 + tap(d2 + 1, n) * (1 - g1)
        psWrite = (psWrite + 1) % n
        return out
    }

    private func tap(_ delay: Float, _ n: Int) -> Float {
        var pos = Float(psWrite) - delay
        while pos < 0 { pos += Float(n) }
        let i0 = Int(pos)
        let frac = pos - Float(i0)
        return psBuf[i0 % n] * (1 - frac) + psBuf[(i0 + 1) % n] * frac
    }

    private func ring(_ x: Float, _ freq: Double, _ mix: Float) -> Float {
        ringPhase += 2 * Double.pi * freq / Double(sampleRate)
        if ringPhase > 2 * Double.pi { ringPhase -= 2 * Double.pi }
        return x * (1 - mix) + x * Float(sin(ringPhase)) * mix
    }

    private func comb(_ x: Float, _ seconds: Float, _ feedback: Float, _ mix: Float) -> Float {
        let n = dlBuf.count
        let d = min(n - 1, max(1, Int(seconds * Float(sampleRate))))
        var r = dlWrite - d
        if r < 0 { r += n }
        let delayed = dlBuf[r]
        dlBuf[dlWrite] = x + delayed * feedback
        dlWrite = (dlWrite + 1) % n
        return x + delayed * mix
    }

    private func radio(_ x: Float) -> Float {
        let dt = 1 / Float(sampleRate)
        let rc = 1 / (2 * Float.pi * 300)
        let a = rc / (rc + dt)
        let hp = a * (hpPrevOut + x - hpPrevIn)
        hpPrevIn = x
        hpPrevOut = hp
        let b = dt / (1 / (2 * Float.pi * 3000) + dt)
        lp1 += b * (hp - lp1)
        lp2 += b * (lp1 - lp2)
        return softClip(lp2 * 4) * 0.7
    }

    private func softClip(_ x: Float) -> Float {
        if x > 1 { return 2.0 / 3.0 }
        if x < -1 { return -2.0 / 3.0 }
        return x - x * x * x / 3
    }
}
