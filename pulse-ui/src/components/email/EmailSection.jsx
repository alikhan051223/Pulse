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
    isLoading = false,
    hasMore = true,
    setPage,
}) {
    const handleScroll = (e) => {
        const { scrollTop, scrollHeight, clientHeight } = e.target;
        const isNearBottom = scrollHeight - scrollTop <= clientHeight + 50;

        if (isNearBottom && hasMore && !isLoading && setPage) {
            setPage((prevPage) => prevPage + 1);
        }
    };

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

            {/* Scrollable Email Feed */}
            <div
                onScroll={handleScroll}
                className="h-full overflow-y-auto flex flex-col divide-y divide-slate-800"
            >
                {emails.map((email) => (
                    <EmailItem
                        key={email.emailID}
                        email={email}
                        isSelected={email.emailID === selectedEmailId}
                        onSelect={onSelectEmail}
                    />
                ))}

                {isLoading && (
                    <div className="p-3 text-center text-xs text-slate-400">
                        Loading more emails...
                    </div>
                )}
            </div>
        </aside>
    )
}