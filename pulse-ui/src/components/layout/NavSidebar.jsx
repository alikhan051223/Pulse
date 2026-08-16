import NavItem from './NavItem'

/**
 * NavSidebar Component
 * Collapsible navigation drawer sidebar listing mail folders and filters
 */
export default function NavSidebar({
    isOpen,
    onClose,
    navItems = [],
    activeFolder,
    onSelectFolder,
}) {
    return (
        <aside
            className={`relative z-20 flex flex-col border-r border-white/10 bg-slate-900/50 backdrop-blur-xl transition-all duration-300 ease-in-out ${
                isOpen ? 'w-64 opacity-100' : 'w-0 opacity-0 overflow-hidden border-none'
            }`}
            aria-hidden={!isOpen}
        >
            <div className="flex h-16 items-center justify-between border-b border-white/10 px-4 shrink-0">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Mailboxes
                </span>
                <button
                    type="button"
                    onClick={onClose}
                    aria-label="Close menu"
                    className="rounded-lg px-2 py-1 text-sm text-slate-400 transition hover:bg-white/5 hover:text-slate-200"
                >
                    ✕
                </button>
            </div>

            <nav className="flex flex-1 flex-col gap-1 p-3 overflow-y-auto">
                {navItems.map((item) => (
                    <NavItem
                        key={item.id}
                        item={item}
                        isActive={activeFolder === item.id}
                        onSelect={onSelectFolder}
                    />
                ))}
            </nav>
        </aside>
    )
}
