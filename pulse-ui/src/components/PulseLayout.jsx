import { useEffect, useState } from 'react'
import BackgroundGlow from './layout/BackgroundGlow'
import Header from './layout/Header'
import NavSidebar from './layout/NavSidebar'
import EmailSection from './email/EmailSection'
import EmailScreen from './ScreenViews/EmailScreen'
import ComposeScreen from './ScreenViews/ComposeScreen'
import SettingsScreen from './ScreenViews/SettingsScreen'
import DefaultScreen from './ScreenViews/DefaultScreen'

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

    // ---------------------------------------------------------------------------
    // 2. Effects & Persistence
    // ---------------------------------------------------------------------------

    // Sync layout preferences to localStorage
    useEffect(() => {
        localStorage.setItem('pulse_nav_pos', navPosition)
    }, [navPosition])

    useEffect(() => {
        localStorage.setItem('pulse_feed_pos', feedPosition)
    }, [feedPosition])

    // Poll backend sync status while the sync modal is active
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

    const handleSelectEmail = (email) => {
        setSelectedEmailId(email?.id || null)
        setViewData(email)
        setCurrentView('VIEW_EMAIL')
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
        // Implement filter actions if needed
    }

    const handleSyncNew = () => {
        setIsWindowOpen(true)
    }

    const stopFullSync = async () => {
        setIsWindowOpen(false)

        try {
            const response = await fetch('http://localhost:8080/api/emails/save-all/stop', {
                method: 'POST',
            })

            const data = await response.text() // Use .text() for ResponseEntity<String>

            if (!response.ok) {
                throw new Error(`Failed to stop sync: ${response.status}`)
            }

            console.log('Stop requested:', data)
        } catch (error) {
            console.error('Failure stopping sync:', error)
        }
    }

    const handlePulse = async () => {
        setIsWindowOpen(true)

        try {
            const response = await fetch('http://localhost:8080/api/emails/save-all', {
                method: 'POST',
            })

            const textData = await response.text() // Use .text() for ResponseEntity<String>

            if (!response.ok) {
                console.warn('Sync notice:', textData)
                return
            }

            console.log('Sync initialized:', textData)
        } catch (error) {
            console.error('Failure starting sync:', error)
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
            emails={[]}
            selectedEmailId={selectedEmailId}
            onSelectEmail={handleSelectEmail}
            onFilter={handleFilter}
            onSyncNew={handleSyncNew}
        />
    )

    return (
        <div className="relative flex h-screen w-screen flex-col overflow-hidden bg-slate-950 text-slate-100">
            {/* Background glow effects */}
            <BackgroundGlow />

            {/* Header */}
            <Header
                brandName={brandName}
                onPulse={handlePulse}
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                onCompose={handleCompose}
                onSettings={handleSettings}
                onToggleNav={handleToggleNav}
            />


            {isWindowOpen && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
                >
                    <div
                        onClick={(e) => e.stopPropagation()}
                        className="bg-slate-900 rounded-xl shadow-2xl w-full max-w-md p-6 border border-slate-700"
                    >
                        <h3 className="text-xl font-semibold text-gray-300 text-center mb-2">Syncing Emails...</h3>
                        <p className="text-gray-300 mb-6 text-center ">This window will close automatically when done</p>

                        <div className="flex justify-center">
                            <button
                                onClick={stopFullSync}
                                className="bg-slate-700 hover:bg-slate-600 text-gray-300 font-medium px-4 py-2 rounded-lg transition-colors"
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
                <main className="relative z-10 flex min-h-0 flex-1 p-5 transition-all duration-300 ease-in-out">
                    <section className="flex h-full w-full flex-col rounded-xl border border-white/10 bg-slate-900/50 p-6 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.06)] backdrop-blur-xl">
                        {currentView === 'VIEW_EMAIL' && (
                            <EmailScreen
                                email={viewData}
                                onClose={() => setCurrentView('EMPTY')}
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