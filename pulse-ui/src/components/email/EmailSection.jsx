
import EmailItem from './EmailItem'

/**
 * EmailSidebar Component
 * Sidebar panel containing filter and sync actions and scrollable email feed
 */
export default function EmailSection({
    emails = [],
    selectedEmailId,
    onSelectEmail,
    onFilter,
    onSyncNew,
}) {
    return (
        <aside className="flex w-96 shrink-0 flex-col border-x border-white/10 bg-slate-900/50 backdrop-blur-xl">
            {/* Top header area of sidebar containing Filter and Sync New buttons */}
            <div className="flex h-16 gap-2 shrink-0 border-b border-white/10 px-4 py-3.5">
                <button
                    type="button"
                    onClick={onFilter}
                    className="w-full rounded-lg border border-blue-500/40 bg-slate-900/40 px-4 py-2 text-sm font-medium text-blue-400 backdrop-blur-xl transition hover:border-blue-500/70 hover:bg-white/5 hover:shadow-[0_0_16px_rgba(59,130,246,0.2)]"
                >
                    Filter
                </button>

                <button
                    type="button"
                    onClick={onSyncNew}
                    className="w-full rounded-lg border border-blue-500/40 bg-slate-900/40 px-4 py-2 text-sm font-medium text-blue-400 backdrop-blur-xl transition hover:border-blue-500/70 hover:bg-white/5 hover:shadow-[0_0_16px_rgba(59,130,246,0.2)]"
                >
                    Sync New
                </button>
            </div>

            {/* Email List Feed / Empty State */}
            <div className="flex-1 overflow-y-auto p-3 space-y-2">
                    <div className="flex h-48 items-center justify-center text-center text-sm text-slate-500">
                        No emails found. Press pulse to save emails
                    </div>
            </div>
        </aside>
    )
}
