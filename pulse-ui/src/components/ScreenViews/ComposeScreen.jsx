import { useState, useRef } from 'react'
import toast from 'react-hot-toast'

export default function ComposeScreen({ onSend, onCancel }) {
    const [to, setTo] = useState('')
    const [subject, setSubject] = useState('')
    const [body, setBody] = useState('')
    const [files, setFiles] = useState([]) // Array for multiple files

    const fileInputRef = useRef(null)

    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files)
        // Append new files to the existing array (prevents duplicates by name)
        setFiles(prevFiles => {
            const existingNames = new Set(prevFiles.map(f => f.name))
            const uniqueNewFiles = selectedFiles.filter(f => !existingNames.has(f.name))
            return [...prevFiles, ...uniqueNewFiles]
        })
        // Reset input value so selecting the same file again still fires onChange
        e.target.value = null
    }

    const removeFile = (fileName) => {
        setFiles(prevFiles => prevFiles.filter(f => f.name !== fileName))
    }

    const handleSubmit = async (e) => {
        e.preventDefault()

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailRegex.test(to)) {
            toast.error('Please enter a valid email address.')
            return
        }

        const formData = new FormData()

        // 1. Create the JSON object matching EmailParams
        const emailParams = {
            toEmailAddress: to,
            subject: subject,
            bodyText: body
        }

        // 2. Append 'request' part as a JSON Blob
        formData.append(
            'request',
            new Blob([JSON.stringify(emailParams)], { type: 'application/json' })
        )

        // 3. Append 'files' part
        files.forEach(file => {
            formData.append('files', file)
        })

        const toastId = toast.loading('Sending...')

        try {
            const response = await fetch('http://localhost:8080/api/emails/send', {
                method: 'POST',
                body: formData
            })

            if (!response.ok) {
                toast.error(`Failed to send email: ${response.statusText}`, { id: toastId })
                return
            }

            toast.success('Email sent successfully!', { id: toastId })
            if (onSend) onSend(formData)
        } catch (error) {
            toast.error(`Error sending request: ${error.message}`, { id: toastId })
        }
    }

    return (
        <form onSubmit={handleSubmit} className="flex h-full flex-col gap-4 text-slate-100">
            {/* Header */}
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
                <h2 className="text-lg font-semibold text-slate-100">New Message</h2>
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={onCancel}
                        className="rounded-lg px-3 py-1.5 text-xs text-slate-400 transition hover:bg-white/5 hover:text-slate-200"
                    >
                        Discard
                    </button>
                    <button
                        type="submit"
                        className="rounded-lg bg-blue-600 px-4 py-1.5 text-xs font-semibold text-white shadow-sm shadow-blue-600/20 transition hover:bg-blue-500 hover:shadow-blue-500/30"
                    >
                        Send
                    </button>
                </div>
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

            {/* Hidden File Input (multiple enabled) */}
            <input
                type="file"
                ref={fileInputRef}
                onChange={handleFileChange}
                multiple
                className="hidden"
            />

            {/* Selected Files List & Attachment Button */}
            <div className="flex flex-col gap-2 pt-2">
                {files.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                        {files.map(f => (
                            <span
                                key={f.name}
                                className="flex items-center gap-1.5 rounded-md bg-slate-800/80 px-2.5 py-1 text-xs text-slate-300 border border-white/5"
                            >
                                {f.name}
                                <button
                                    type="button"
                                    onClick={() => removeFile(f.name)}
                                    className="ml-1 text-slate-400 hover:text-slate-100"
                                >
                                    ✕
                                </button>
                            </span>
                        ))}
                    </div>
                )}

                <div className="flex justify-end">
                    <button
                        type="button"
                        onClick={() => fileInputRef.current.click()}
                        className="flex items-center gap-2 rounded-lg border border-white/10 bg-slate-900/40 px-4 py-2 text-sm text-slate-300 transition hover:bg-white/5 hover:text-slate-100"
                    >
                        Add Attachment
                    </button>
                </div>
            </div>
        </form>
    )
}