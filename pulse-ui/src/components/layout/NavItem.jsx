/**
 * NavItem Component
 * Renders an individual navigation folder button with active state and count badge
 */
export default function NavItem({ item, isActive, onSelect }) {
    return (
        <button
            type="button"
            onClick={() => onSelect(item.id)}
            className={`flex items-center gap-3 rounded-lg px-4 py-3 text-left text-sm transition ${
                isActive
                    ? 'bg-slate-800/50 text-blue-400 shadow-[inset_3px_0_0_0_rgb(59,130,246)] backdrop-blur-xl'
                    : 'text-slate-300 hover:bg-white/5 hover:text-slate-100'
            }`}
        >
            <span className="font-medium whitespace-nowrap">{item.label}</span>
            {item.count !== null && item.count !== undefined && (
                <span
                    className={`ml-auto rounded-full px-2 py-0.5 text-xs font-semibold ${
                        isActive ? 'bg-blue-600 text-white' : 'bg-blue-600/20 text-blue-400'
                    }`}
                >
                    {item.count}
                </span>
            )}
        </button>
    )
}
