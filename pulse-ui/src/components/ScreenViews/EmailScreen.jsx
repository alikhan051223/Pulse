export default function EmailScreen({ email, onClose }) {
    if (!email) return null
    return (
        <div className="flex h-full flex-col text-slate-100">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
                <div>
                    <h2 className="text-xl font-bold">{email.subject}</h2>
                    <p className="mt-1 text-sm text-slate-400">
                        From: <span className="text-slate-200">{email.sender}</span> • {email.timestamp}
                    </p>
                </div>
                <button
                    type="button"
                    onClick={onClose}
                    className="rounded-lg border border-white/10 bg-slate-900/40 px-3 py-1.5 text-xs text-slate-300 hover:bg-white/5"
                >
                    ✕
                </button>
            </div>
            <div className="flex-1 overflow-y-auto py-4 text-slate-300 leading-relaxed">
                <p>{email.preview}</p>
                <div className="mt-6 rounded-lg border border-white/5 bg-slate-900/30 p-4 text-sm text-slate-400">
                    [ Full email body content goes here ]
                </div>
            </div>
        </div>
    )
}