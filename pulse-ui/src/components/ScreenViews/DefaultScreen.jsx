export default function DefaultScreen() {
    return (
        <div className="flex h-full w-full items-center justify-center">
            <div className="max-w-md px-6 text-center">
                <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl border border-white/10 bg-slate-900/40 text-3xl text-slate-600 backdrop-blur-xl">
                    ✉️
                </div>
                <p className="text-base leading-relaxed text-slate-400">
                    Select an email from the list to view its contents, or compose a new email.
                </p>
            </div>
        </div>
    )
}