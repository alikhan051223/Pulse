import NavItem from './NavItem'

/**
 * NavSidebar Component
 * Collapsible navigation drawer sidebar listing mail folders and filters
 */
export default function NavSidebar({
    isOpen,
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
