
export default function EmailItem({ email, isSelected, onSelect }) {
    const formattedDate = email.dateSent
        ? new Date(email.dateSent).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : '';


    return (
        <div
            onClick={() => onSelect(email.emailID)}
            className={`cursor-pointer p-4 transition-colors hover:bg-slate-800/50 ${
                isSelected ? 'bg-slate-800/80 border-l-2 border-blue-500' : ''
            }`}
        >
            {/* Top Row: Sender & Date */}
            <div className="flex items-center justify-between mb-1">
                <span className="font-semibold text-sm text-slate-100 truncate max-w-[200px]">
                    {email.fromEmailAddress || 'Unknown Sender'}
                </span>
                <span className="text-xs text-slate-400 whitespace-nowrap ml-2">
                    {formattedDate}
                </span>
            </div>

            {/* Subject Line */}
            <h4 className="text-sm font-medium text-slate-300 truncate mb-1">
                {email.subject || '(No Subject)'}
            </h4>

            {/* Snippet Preview */}
            <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                {email.snippet || 'No preview available'}
            </p>
        </div>
    );
}

