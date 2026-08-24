import { useEffect, useState } from 'react'
import BackgroundGlow from './layout/BackgroundGlow'
import Header from './layout/Header'
import NavSidebar from './layout/NavSidebar'
import EmailSection from './email/EmailSection'
import EmailScreen from './ScreenViews/EmailScreen'
import ComposeScreen from './ScreenViews/ComposeScreen'
import SettingsScreen from './ScreenViews/SettingsScreen'
import DefaultScreen from './ScreenViews/DefaultScreen'
import { Toaster } from 'react-hot-toast'
import toast from 'react-hot-toast'

// Default mail navigation folders
const NAV_ITEMS = [
    { id: 'inbox', label: 'Inbox' },
    { id: 'starred', label: 'Starred' },
    { id: 'sent', label: 'Sent' },
    { id: 'drafts', label: 'Drafts' },
    { id: 'trash', label: 'Trash' },
]

/**
 * Main application layout component
 */
export default function PulseLayout() {
    // ---------------------------------------------------------------------------
    // 1. State Declarations
    // ---------------------------------------------------------------------------

    // Layout configuration with localStorage persistence
    const [navPosition, setNavPosition] = useState(
        () => localStorage.getItem('pulse_nav_pos') || 'left'
    )
    const [feedPosition, setFeedPosition] = useState(
        () => localStorage.getItem('pulse_feed_pos') || 'right'
    )

    // Active view & screen state
    const [currentView, setCurrentView] = useState('EMPTY')
    const [viewData, setViewData] = useState(null)
    const [brandName] = useState('Pulse')

    // Navigation drawer & modal states
    const [isNavOpen, setIsNavOpen] = useState(false)
    const [isWindowOpen, setIsWindowOpen] = useState(false)

    // Selection & search state
    const [selectedEmailId, setSelectedEmailId] = useState(null)
    const [activeFolder, setActiveFolder] = useState('inbox')
    const [searchQuery, setSearchQuery] = useState('')

    // getting emails
    const [emails, setEmails] = useState([])
    const [page, setPage] = useState(0)
    const [hasMore, setHasMore] = useState(true)
    const [isLoading, setIsLoading] = useState(false)


    // ---------------------------------------------------------------------------
    // 2. Effects & Persistence
    // ---------------------------------------------------------------------------
    // get page of emails on startUp
    useEffect(() => {
        if (!hasMore || isLoading) return;
        setIsLoading(true);

        const getEmailSummaries = async () => {
            try {
                const response = await fetch(`http://localhost:8080/api/emails/summaries?page=${page}&size=20`,  {
                    method:'GET'})
                if (!response.ok) {
                    console.error('Failed to fetch summaries:', response.statusText);
                    toast.error(`Failed to fetch summaries: ${response.statusText}`, {
                        id: 'fetch-summaries-error-backend',
                    });
                    return;
                }

                const data = await response.json();
                setEmails((prevEmails) => {

                    const existingIds = new Set(prevEmails.map((e) => e.emailID));

                    const newEmails = data.content.filter((e) => !existingIds.has(e.emailID));

                    return [...prevEmails, ...newEmails]; })
                setHasMore(!data.last);
            } catch (error) {
                console.error('Error fetching summaries:', error.message);
                toast.error(`Error fetching summaries: ${error.message}`, {
                    id: 'fetch-summaries-error-frontend',
            });
            } finally {
                setIsLoading(false);
            }
        };

        void getEmailSummaries();
    }, [page]);

    // Sync layout preferences to localStorage
    useEffect(() => {
        localStorage.setItem('pulse_nav_pos', navPosition)
    }, [navPosition])

    useEffect(() => {
        localStorage.setItem('pulse_feed_pos', feedPosition)
    }, [feedPosition])

    // Poll backend sync status while the sync window is active
    useEffect(() => {
        if (!isWindowOpen) return

        let isCancelled = false

        const checkSyncStatus = async () => {
            try {
                const response = await fetch('http://localhost:8080/api/emails/save-all/check')
                if (response.ok && !isCancelled) {
                    const isRunning = await response.json()
                    if (!isRunning) {
                        console.log('Sync completed automatically on backend.')
                        toast.success('Sync complete!')
                        setIsWindowOpen(false)
                    }
                }
            } catch (error) {
                if (!isCancelled) {
                    console.error('Failed to poll sync status:', error)
                }
            }
        }
        checkSyncStatus()
        const intervalId = setInterval(checkSyncStatus, 2000)

        return () => {
            isCancelled = true
            clearInterval(intervalId)
        }
    }, [isWindowOpen])

    // display a full email when a summary is selected
    useEffect(() => {
        if (!selectedEmailId) return;

        const fetchFullEmail = async () => {
            try {
                const response = await fetch(`http://localhost:8080/api/emails/${selectedEmailId}`)

                if (!response.ok) {
                    console.error('Failed to get email')
                }

                const emailInfo = await response.json()
                setViewData(emailInfo)
                setCurrentView('VIEW_EMAIL')
                console.log(emailInfo)

            } catch (error) {
                console.error('Error fetching full email:', error)
            }
        }
        void fetchFullEmail()
    }, [selectedEmailId])

    // ---------------------------------------------------------------------------
    // 3. Navigation & View Handlers
    // ---------------------------------------------------------------------------

    const handleToggleNav = () => setIsNavOpen((open) => !open)
    const handleCloseNav = () => setIsNavOpen(false)

    const handleSelectFolder = (folderId) => {
        setActiveFolder(folderId)
        setSelectedEmailId(null)
        setViewData(null)
        setCurrentView('EMPTY')
    }

    const handleCompose = () => {
        setViewData(null)
        setCurrentView('COMPOSE_EMAIL')
    }

    const handleSettings = () => {
        setCurrentView('SETTINGS')
    }

    // ---------------------------------------------------------------------------
    // 4. Sync & Action Handlers
    // ---------------------------------------------------------------------------

    const handleFilter = () => {
        // Implement filter
    }

    const stopFullSync = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/emails/save-all/stop', {
                method: 'POST',
            })

            const data = await response.text() // Use .text() for ResponseEntity<String>

            if (!response.ok) {
                throw new Error(`Failed to stop sync: ${response.status}`)
            }
            setIsWindowOpen(false)
            console.log('Sync cancelled', response)
            toast.success(`Sync cancelled`)
        } catch (error) {
            console.error('Failure stopping sync:', error)
        }
    }

    const handleSyncNew = async () => {
        const toastId = toast.loading('Loading...');
        try {
            const response = await fetch('http://localhost:8080/api/emails/save-all', {
                method: 'POST',
            })

            const textData = await response.text()

            if (!response.ok) {
                console.warn('Error:', textData)
                toast.error(textData || `Server error (${response.status})`)
                return
            }

            toast.dismiss(toastId);
            setIsWindowOpen(true)
            console.log('Sync initialized:', textData)
        } catch (error) {
            console.error('Failure starting sync:', error)
            toast.error(error.message || 'Error saving emails')
        }
    }

    const navSidebarComponent = (
        <NavSidebar
            isOpen={isNavOpen}
            onClose={handleCloseNav}
            navItems={NAV_ITEMS}
            activeFolder={activeFolder}
            onSelectFolder={handleSelectFolder}
        />
    )

    const emailFeedSidebarComponent = (
        <EmailSection
            emails={emails}
            selectedEmailId={selectedEmailId}
            onSelectEmail={setSelectedEmailId}
            isLoading={isLoading}
            hasMore={hasMore}
            setPage={setPage}
            onFilter={handleFilter}
            onSyncNew={handleSyncNew}
        />
    )

    return (
        <div className="relative flex h-screen w-screen flex-col overflow-hidden bg-slate-950 text-slate-100">
            {/* Background glow effects */}
            <BackgroundGlow />

            <Toaster
                position="top-center"
                toastOptions={{
                    duration: 1500,
                    // Global styling for all toasts
                    style: {
                        background: '#0f172a',
                        color: '#f8fafc',
                        border: '1px solid rgba(255, 255, 255, 0.1)',
                        borderRadius: '0.75rem', // rounded-xl
                        boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)',
                        fontSize: '0.875rem',
                    },

                    error: {
                        style: {
                            border: '1px solid rgba(239, 68, 68, 0.3)',
                        },
                        iconTheme: {
                            primary: '#ef4444',
                            secondary: '#0f172a',
                        },
                    },
                    success: {
                        style: {
                            border: '1px solid rgba(34, 197, 94, 0.3)',
                        },
                        iconTheme: {
                            primary: '#22c55e',
                            secondary: '#0f172a',
                        },
                    },
                }}
            />

            {/* Header */}
            <Header
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                onCompose={handleCompose}
                onSettings={handleSettings}
                onToggleNav={handleToggleNav}
            />

            {isWindowOpen && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4 backdrop-blur-none transition-all"
                >
                    <div
                        onClick={(e) => e.stopPropagation()}
                        className="relative w-full max-w-md overflow-hidden rounded-xl border border-white/10 bg-slate-900/50 p-6 shadow-2xl backdrop-blur-xl shadow-[inset_0_1px_0_0_rgba(255,255,255,0.06)] text-slate-100"
                    >
                        <div className="flex flex-col items-center text-center">
                            <h3 className="text-lg font-bold tracking-tight text-slate-100">
                                Syncing Emails...
                            </h3>
                            <p className="mt-1 text-xs text-slate-400">
                                This window will close automatically when finished.
                            </p>
                        </div>

                        <div className="mt-6 flex justify-center border-t border-white/10 pt-4">
                            <button
                                type="button"
                                onClick={stopFullSync}
                                className="shrink-0 rounded-lg bg-blue-900 px-4 py-2 text-sm font-semibold text-white shadow-sm shadow-blue-600/20 transition hover:bg-blue-500 hover:shadow-blue-500/30"
                            >
                                Cancel Sync
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Dynamic 3-Pane Body Layout */}
            <div className="relative z-10 flex min-h-0 flex-1 overflow-hidden">
                {/* Render Nav on LEFT if navPosition is 'left' */}
                {navPosition === 'left' && navSidebarComponent}

                {/* Render Feed on LEFT if feedPosition is 'left' */}
                {feedPosition === 'left' && emailFeedSidebarComponent}

                {/* Center Main Reading / Content View */}
                <main className="relative z-10 flex min-h-0 min-w-0 flex-1 overflow-hidden p-5 transition-all duration-300 ease-in-out">
                    <section className="flex h-full w-full min-h-0 min-w-0 flex-col overflow-hidden rounded-xl border border-white/10 bg-slate-900/50 p-6 shadow-[inset_0_1px_0_0_rgba(255,
  255,255,0.06)] backdrop-blur-xl">
                        {currentView === 'VIEW_EMAIL' && (
                            <EmailScreen
                                email={viewData}
                                onClose={() => {
                                    setCurrentView('EMPTY')
                                    setSelectedEmailId(null)
                                }}
                            />
                        )}

                        {currentView === 'COMPOSE_EMAIL' && (
                            <ComposeScreen
                                onSend={(data) => {
                                    console.log('Sending email data:', data)
                                    setCurrentView('EMPTY')
                                }}
                                onCancel={() => setCurrentView('EMPTY')}
                            />
                        )}

                        {currentView === 'SETTINGS' && (
                            <SettingsScreen
                                navPosition={navPosition}
                                setNavPosition={setNavPosition}
                                feedPosition={feedPosition}
                                setFeedPosition={setFeedPosition}
                            />
                        )}

                        {currentView === 'EMPTY' && <DefaultScreen />}
                    </section>
                </main>
                {feedPosition === 'right' && emailFeedSidebarComponent}
                {navPosition === 'right' && navSidebarComponent}
            </div>
        </div>
    )
}