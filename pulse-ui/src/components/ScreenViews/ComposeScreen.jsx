import { useState } from 'react'

export default function ComposeScreen({ onSend, onCancel }) {
    const [to, setTo] = useState('')
    const [subject, setSubject] = useState('')
    const [body, setBody] = useState('')

    const handleSubmit = async (e) => {
        e.preventDefault()

        const emailData = {
            toEmailAddress: to,
            subject: subject,
            bodyText: body,
            files: null
        }

        try {
            const response = await fetch('http://localhost:8080/api/emails/send', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(emailData)
            })

            if (response.ok) {
                if (onSend) onSend(emailData)
            } else {
                console.error('Failed to send email:', response.statusText)
            }
        } catch (error) {
            console.error('Error sending request:', error)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="flex h-full flex-col gap-4 text-slate-100">
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
                <h2 className="text-lg font-semibold text-slate-100">New Message</h2>
                <button
                    type="button"
                    onClick={onCancel}
                    className="text-xs text-slate-400 transition hover:text-slate-200"
                >
                    Cancel
                </button>
            </div>

            {/* To Input */}
            <input
                type="email"
                placeholder="To"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                required
                className="w-full rounded-lg border border-white/10 bg-slate-900/40 px-4 py-2 text-sm text-slate-100 placeholder-slate-500 outline-none backdrop-blur-xl transition focus:border-blue-500/60 focus:ring-2 focus:ring-blue-500/30"
            />

            {/* Subject Input */}
            <input
                type="text"
                placeholder="Subject"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                required
                className="w-full rounded-lg border border-white/10 bg-slate-900/40 px-4 py-2 text-sm text-slate-100 placeholder-slate-500 outline-none backdrop-blur-xl transition focus:border-blue-500/60 focus:ring-2 focus:ring-blue-500/30"
            />

            {/* Body Textarea */}
            <textarea
                placeholder="Write your email here..."
                value={body}
                onChange={(e) => setBody(e.target.value)}
                required
                className="flex-1 resize-none rounded-lg border border-white/10 bg-slate-900/40 p-4 text-sm text-slate-100 placeholder-slate-500 outline-none backdrop-blur-xl transition focus:border-blue-500/60 focus:ring-2 focus:ring-blue-500/30"
            />

            {/* Form Action Buttons */}
            <div className="flex justify-end gap-2 pt-2">
                <button
                    type="button"
                    onClick={onCancel}
                    className="rounded-lg px-4 py-2 text-sm text-slate-400 transition hover:bg-white/5 hover:text-slate-200"
                >
                    Discard
                </button>
                <button
                    type="submit"
                    className="rounded-lg bg-blue-600 px-5 py-2 text-sm font-semibold text-white shadow-sm shadow-blue-600/20 transition hover:bg-blue-500 hover:shadow-blue-500/30"
                >
                    Send Email
                </button>
            </div>
        </form>
    )
}