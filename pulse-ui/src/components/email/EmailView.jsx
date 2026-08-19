import React from 'react';

export default function EmailView({ htmlContent }) {
    if (!htmlContent) return <p className="text-slate-400">No content</p>;

    const isHtml = /<[a-z][\s\S]*>/i.test(htmlContent);

    if (!isHtml) {
        return (
            <div className="w-full h-full p-4 text-sm text-slate-800 whitespace-pre-wrap break-words rounded-lg overflow-auto">
                {htmlContent}
            </div>
        );
    }

    return (
        <iframe
            title="Email Content"
            srcDoc={`<base target="_blank">${htmlContent}`}
            sandbox="allow-popups allow-popups-to-escape-sandbox allow-same-origin"
            className="w-full h-full border-0 rounded-lg bg-white"

        />
    );
}