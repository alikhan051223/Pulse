/**
 * BackgroundGlow Component
 * Renders decorative background radial gradient glow effects
 */
export default function BackgroundGlow() {
    return (
        <div
            className="pointer-events-none fixed inset-0 z-0 overflow-hidden"
            aria-hidden="true"
        >
            <div className="absolute -left-24 top-[18%] h-[28rem] w-[28rem] rounded-full bg-blue-600/25 blur-[120px]" />
            <div className="absolute -right-20 bottom-[12%] h-80 w-80 rounded-full bg-sky-500/20 blur-[120px]" />
        </div>
    )
}
