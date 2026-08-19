import EmailView from '../email/EmailView'

export default function EmailScreen({ email, onClose }) {
    if (!email) return null

    let time = ''
    let date = ''

    if (email.dateSent) {
        const emailDate = new Date(email.dateSent)

        const year = emailDate.getFullYear()
        const month = emailDate.getMonth() + 1
        const day = emailDate.getDate()
        const hour = emailDate.getHours()
        const minute = emailDate.getMinutes()

        time = `${hour}:${minute.toString().padStart(2, '0')}`
        date = `${day}-${month}-${year}`
    }

    // Fall back to plainTextBody or snippet if htmlBody is empty
    const emailContent = email.htmlBody || email.plainTextBody || email.snippet || ''

    return (
        <div className="flex h-full w-full min-h-0 min-w-0 flex-col overflow-hidden text-slate-100">
            {/* Header Toolbar */}
            <div className="flex shrink-0 items-start justify-between gap-4 border-b border-white/10 pb-4">
                <div className="min-w-0 flex-1">
                    <h2 className="truncate text-xl font-bold">{email.subject || '(No Subject)'}</h2>
                    <div className="mt-1 flex items-center justify-between gap-2 text-sm text-slate-400">
                        <p className="truncate">
                            From: <span className="text-slate-200">{email.fromEmailAddress || email.sender}</span>
                        </p>
                        <p className="shrink-0 whitespace-nowrap">{time} • {date}</p>
                    </div>
                </div>
                <button
                    type="button"
                    onClick={onClose}
                    className="shrink-0 rounded-lg px-3 py-1.5 text-xs text-slate-300 hover:bg-white/5"
                >
                    ✕
                </button>
            </div>

            {/* Email View Container */}
            <div className="flex flex-1 min-h-0 min-w-0 w-full flex-col pt-4 overflow-hidden">
                <EmailView htmlContent={emailContent} />
            </div>
        </div>
    )
}