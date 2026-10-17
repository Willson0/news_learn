import AVFoundation
import SwiftUI

/// Запись и воспроизведение голосовых сообщений чата.
/// Запись — AVAudioRecorder (m4a/AAC), воспроизведение — AVPlayer по подписанной ссылке.
@MainActor
final class ChatAudio: NSObject, ObservableObject {
    @Published var recording = false
    @Published var elapsed = 0
    @Published var playingId: Int? = nil

    private var recorder: AVAudioRecorder?
    private var recordURL: URL?
    private var timer: Timer?
    private var player: AVPlayer?
    private var endObserver: NSObjectProtocol?

    // MARK: - Разрешение на микрофон

    func requestMicPermission(_ completion: @escaping (Bool) -> Void) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async { completion(granted) }
        }
    }

    // MARK: - Запись

    @discardableResult
    func startRecording() -> Bool {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
        } catch { return false }

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("voice-\(UUID().uuidString).m4a")
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
        ]
        do {
            let r = try AVAudioRecorder(url: url, settings: settings)
            r.record()
            recorder = r
            recordURL = url
            recording = true
            elapsed = 0
            timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.elapsed += 1 }
            }
            return true
        } catch {
            return false
        }
    }

    /// Останавливает запись и возвращает данные и длительность (сек), либо nil.
    func stopRecording() -> (data: Data, duration: Int)? {
        timer?.invalidate(); timer = nil
        recording = false
        guard let r = recorder, let url = recordURL else { return nil }
        let dur = Int(r.currentTime.rounded())
        r.stop()
        recorder = nil
        recordURL = nil
        let data = try? Data(contentsOf: url)
        try? FileManager.default.removeItem(at: url)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        guard let data = data, !data.isEmpty else { return nil }
        return (data, max(dur, 1))
    }

    func cancelRecording() {
        timer?.invalidate(); timer = nil
        recording = false
        recorder?.stop()
        recorder = nil
        if let url = recordURL { try? FileManager.default.removeItem(at: url) }
        recordURL = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: - Воспроизведение

    func togglePlay(id: Int, url: URL?) {
        if playingId == id { stopPlayback(); return }
        guard let url = url else { return }
        stopPlayback()
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {}
        let p = AVPlayer(url: url)
        player = p
        playingId = id
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: p.currentItem, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.stopPlayback() }
        }
        p.play()
    }

    func stopPlayback() {
        player?.pause()
        player = nil
        playingId = nil
        if let o = endObserver {
            NotificationCenter.default.removeObserver(o)
            endObserver = nil
        }
    }
}
