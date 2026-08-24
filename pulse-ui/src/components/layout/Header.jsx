/**
 * Header Component
 * Renders top navigation bar containing branding, search input, compose action button, settings, and drawer toggle button.
 */
export default function Header({
    searchQuery,
    onSearchChange,
    onCompose,
    onSettings,
    onToggleNav,
}) {
    return (
        <header className="relative z-10 flex shrink-0 items-center gap-4 border-b border-white/10 bg-slate-900/50 px-5 py-3 backdrop-blur-xl">
            <div className="flex min-w-0 flex-1 items-center justify-center gap-6">
                <div className="flex shrink-0 items-center gap-2">
                    <span className="h-2 w-2 rounded-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.8)]" />
                    <span className="bg-gradient-to-r from-slate-100 via-blue-200 to-blue-400 bg-clip-text text-xl font-bold tracking-tight text-transparent">
                         Pulse
                    </span>
                </div>

                {/* Search input field and Compose action button */}
                <div className="flex w-full max-w-md items-center gap-3">
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Search emails..."
                        className="w-full rounded-lg border border-white/10 bg-slate-900/40 px-4 py-2 text-sm text-slate-100 placeholder:text-slate-500 outline-none backdrop-blur-xl transition focus:border-blue-500/60 focus:ring-2 focus:ring-blue-500/30"
                    />
                    <button
                        type="button"
                        onClick={onCompose}
                        className="shrink-0 rounded-lg bg-blue-900 px-4 py-2 text-sm font-semibold text-white shadow-sm shadow-blue-600/20 transition hover:bg-blue-500 hover:shadow-blue-500/30"
                    >
                        Compose
                    </button>
                </div>
            </div>

            {/* Right header buttons for settings and toggling navigation menu */}
            <div className="flex shrink-0 items-center gap-1">
                <button
                    type="button"
                    onClick={onSettings}
                    aria-label="Open settings"
                    className="rounded-lg p-2 text-lg text-slate-400 transition hover:bg-white/5 hover:text-slate-200"
                >
                    ⚙️
                </button>

                <button
                    type="button"
                    onClick={onToggleNav}
                    aria-label="Open navigation menu"
                    className="rounded-lg p-2 text-lg text-slate-400 transition hover:bg-white/5 hover:text-blue-400"
                >
                    ☰
                </button>
            </div>
        </header>
    )
}
