export default function SettingsScreen({ navPosition, setNavPosition, feedPosition, setFeedPosition }) {
    return (
        <div className="space-y-6 text-slate-100">
            <h2 className="text-xl font-bold">Layout Settings</h2>

            {/* Navigation Placement */}
            <div className="space-y-2">
                <label className="text-sm font-medium text-slate-300">Navigation Panel Position</label>
                <div className="flex gap-4">
                    <button
                        onClick={() => setNavPosition('left')}
                        className={`rounded-lg px-4 py-2 text-sm border ${
                            navPosition === 'left' ? 'border-blue-500 bg-blue-600/20 text-blue-400' : 'border-white/10 text-slate-400'
                        }`}
                    >
                        Left Side
                    </button>
                    <button
                        onClick={() => setNavPosition('right')}
                        className={`rounded-lg px-4 py-2 text-sm border ${
                            navPosition === 'right' ? 'border-blue-500 bg-blue-600/20 text-blue-400' : 'border-white/10 text-slate-400'
                        }`}
                    >
                        Right Side
                    </button>
                </div>
            </div>

            {/* Email Feed Placement */}
            <div className="space-y-2">
                <label className="text-sm font-medium text-slate-300">Email Feed Position</label>
                <div className="flex gap-4">
                    <button
                        onClick={() => setFeedPosition('left')}
                        className={`rounded-lg px-4 py-2 text-sm border ${
                            feedPosition === 'left' ? 'border-blue-500 bg-blue-600/20 text-blue-400' : 'border-white/10 text-slate-400'
                        }`}
                    >
                        Left Side
                    </button>
                    <button
                        onClick={() => setFeedPosition('right')}
                        className={`rounded-lg px-4 py-2 text-sm border ${
                            feedPosition === 'right' ? 'border-blue-500 bg-blue-600/20 text-blue-400' : 'border-white/10 text-slate-400'
                        }`}
                    >
                        Right Side
                    </button>
                </div>
            </div>
        </div>
    )
}