// =============================================
// HI-TECH INSTITUTE LMS — Common JavaScript
// Used by ALL pages in the LMS frontend
// =============================================

const API_BASE = 'http://localhost:8080/api';

// ---- Auth Helpers ----

/** Save login data to localStorage after successful login */
function saveAuth(data) {
    localStorage.setItem('lms_token', data.accessToken);
    localStorage.setItem('lms_refresh', data.refreshToken);
    localStorage.setItem('lms_user', JSON.stringify({
        id: data.userId,
        fullName: data.fullName,
        email: data.email,
        role: data.role
    }));
}

/** Get the stored JWT access token */
function getToken() { return localStorage.getItem('lms_token'); }

/** Get the currently logged-in user object */
function getCurrentUser() {
    const u = localStorage.getItem('lms_user');
    return u ? JSON.parse(u) : null;
}

/** Check if the user is logged in */
function isLoggedIn() { return !!getToken(); }

/** Get the user's role (ADMIN, INSTRUCTOR, STUDENT) */
function getUserRole() {
    const user = getCurrentUser();
    return user ? user.role : null;
}

/**
 * Redirect to login if not authenticated.
 * Call this at the top of every protected page.
 */
function requireAuth() {
    if (!isLoggedIn()) {
        window.location.href = 'login.html';
        return false;
    }
    return true;
}

/**
 * Redirect to dashboard if already logged in.
 * Call this on login/register pages.
 */
function redirectIfLoggedIn() {
    if (isLoggedIn()) {
        window.location.href = 'dashboard.html';
    }
}

/** Clear all auth data and redirect to login */
function logout() {
    const refreshToken = localStorage.getItem('lms_refresh');
    if (refreshToken) {
        // Tell the server to invalidate the refresh token
        apiCall(`/auth/logout?refreshToken=${refreshToken}`, { method: 'POST' })
            .catch(() => { }); // Ignore errors — clear local state regardless
    }
    localStorage.clear();
    window.location.href = 'login.html';
}

/** Post-login redirect based on role */
function redirectByRole(role) {
    switch (role) {
        case 'ADMIN': window.location.href = 'dashboard.html'; break;
        case 'INSTRUCTOR': window.location.href = 'dashboard.html'; break;
        case 'STUDENT': window.location.href = 'dashboard.html'; break;
        case 'SUPPORT_STAFF': window.location.href = 'dashboard.html'; break;
        default: window.location.href = 'login.html';
    }
}

// ---- API Helper ----

/**
 * Central API call function.
 * Automatically adds the Authorization header with the JWT token.
 * Handles 401 (session expired) by redirecting to login.
 *
 * Usage:
 *   const data = await apiCall('/auth/login', { method: 'POST', body: { email, password } });
 */
async function apiCall(endpoint, options = {}) {
    const token = getToken();

    const headers = {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...(options.headers || {})
    };

    const config = {
        method: options.method || 'GET',
        headers,
        ...(options.body ? { body: JSON.stringify(options.body) } : {})
    };

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, config);
        const data = await response.json();

        // Handle session expired or deactivated account
        if (response.status === 401) {
            // Don't redirect on login page itself
            if (!window.location.pathname.includes('login')) {
                showToast('Session expired. Please log in again.', 'error');
                setTimeout(() => {
                    localStorage.clear();
                    window.location.href = 'login.html';
                }, 1500);
            }
            throw { status: 401, message: data.message || 'Unauthorized' };
        }

        if (!response.ok) {
            throw { status: response.status, message: data.message || 'Request failed', data };
        }

        return data;

    } catch (err) {
        if (err.status) throw err; // Re-throw our structured errors
        throw { status: 0, message: 'Cannot connect to server. Is the backend running?' };
    }
}

// ---- Toast Notifications ----

/**
 * Show a floating toast notification.
 * @param {string} message - The message to show
 * @param {string} type    - 'success' | 'error' | 'info'
 * @param {number} duration - How long to show (ms), default 3500
 */
function showToast(message, type = 'info', duration = 3500) {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }

    const icons = { success: '✅', error: '❌', info: 'ℹ️' };

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${message}</span>`;

    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('hiding');
        setTimeout(() => toast.remove(), 300);
    }, duration);
}

// ---- DOM Helpers ----

/** Show an element (removes 'hidden' class) */
function show(el) { if (el) el.classList.remove('hidden'); }

/** Hide an element (adds 'hidden' class) */
function hide(el) { if (el) el.classList.add('hidden'); }

/** Set button to loading state */
function setLoading(btn, loading, text = null) {
    if (loading) {
        btn.disabled = true;
        btn._originalHTML = btn.innerHTML;
        btn.innerHTML = `<span class="spinner"></span>${text || 'Please wait...'}`;
    } else {
        btn.disabled = false;
        btn.innerHTML = btn._originalHTML || btn.innerHTML;
    }
}

/** Show a field-level error message */
function showFieldError(inputEl, message) {
    inputEl.classList.add('error');
    let errEl = inputEl.parentElement.querySelector('.form-error');
    if (!errEl) {
        errEl = document.createElement('div');
        errEl.className = 'form-error';
        inputEl.parentElement.appendChild(errEl);
    }
    errEl.textContent = message;
    errEl.classList.add('visible');
}

/** Clear a field error */
function clearFieldError(inputEl) {
    inputEl.classList.remove('error');
    const errEl = inputEl.parentElement.querySelector('.form-error');
    if (errEl) errEl.classList.remove('visible');
}

/** Clear all field errors in a form */
function clearAllErrors(formEl) {
    formEl.querySelectorAll('.form-control.error').forEach(el => el.classList.remove('error'));
    formEl.querySelectorAll('.form-error.visible').forEach(el => el.classList.remove('visible'));
}

/** Show validation errors from API response on form fields */
function showApiErrors(formEl, errorsObj) {
    if (!errorsObj || typeof errorsObj !== 'object') return;
    Object.entries(errorsObj).forEach(([field, message]) => {
        // Try to find input by name attribute
        const input = formEl.querySelector(`[name="${field}"]`);
        if (input) showFieldError(input, message);
    });
}

// ---- Password Strength ----

/**
 * Evaluates password strength and updates a strength meter UI.
 * @param {string} password
 * @param {HTMLElement} barFill - The fill element of the progress bar
 * @param {HTMLElement} labelEl - The text label element
 */
function updatePasswordStrength(password, barFill, labelEl) {
    let score = 0;
    if (password.length >= 8) score++;
    if (/[A-Z]/.test(password)) score++;
    if (/[0-9]/.test(password)) score++;
    if (/[^A-Za-z0-9]/.test(password)) score++;
    if (password.length >= 12) score++;

    const levels = [
        { width: '0%', color: '#e2e8f0', label: '' },
        { width: '25%', color: '#dc2626', label: 'Weak' },
        { width: '50%', color: '#d97706', label: 'Fair' },
        { width: '75%', color: '#2563eb', label: 'Strong' },
        { width: '100%', color: '#16a34a', label: 'Very Strong' },
    ];

    const level = levels[Math.min(score, 4)];
    barFill.style.width = level.width;
    barFill.style.background = level.color;
    if (labelEl) {
        labelEl.textContent = level.label;
        labelEl.style.color = level.color;
    }
}

// ---- Navbar Builder ----

/**
 * Builds the top navigation bar based on the current user's role.
 * Call this on every protected page inside <body>.
 * @param {string} activePage - 'dashboard' | 'users' | 'profile' | 'schedule' | 'exams' | 'finance'
 */
function buildNavbar(activePage = '') {
    const user = getCurrentUser();
    if (!user) return;

    const role = user.role;
    const initials = user.fullName.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);

    // Nav links based on role (FR-1.2: RBAC conditional rendering)

    const navLinks = [];

    if (role !== 'SUPPORT_STAFF') {
        navLinks.push({ href: 'schedule.html', label: 'Schedule', page: 'schedule' });
        navLinks.push({ href: 'exams.html', label: 'Exams', page: 'exams' });
    }

    if (role === 'ADMIN' || role === 'STUDENT') {
        navLinks.push({ href: 'finance.html', label: 'Finance', page: 'finance' });
    }
    if (role === 'STUDENT') {
        navLinks.push({ href: 'my-courses.html', label: 'My Courses', page: 'courses' });
    }
    if (role === 'ADMIN' || role === 'INSTRUCTOR') {
        navLinks.push({ href: 'manage-courses.html', label: 'Courses', page: 'courses' });
    }
    if (role === 'ADMIN') {
        navLinks.push({ href: 'admin-users.html', label: 'Users', page: 'users' });
    }
    // Support link
    if (role !== 'SUPPORT_STAFF') {
        navLinks.push({ href: 'support.html', label: 'Support', page: 'support' });
    }

    const navHTML = navLinks.map(link => `
        <li>
            <a href="${link.href}"
               class="nav-link ${activePage === link.page ? 'active' : ''}">
                ${link.label}
            </a>
        </li>
    `).join('');

    const navbar = document.getElementById('main-navbar');
    if (!navbar) return;

    navbar.innerHTML = `
        <div class="container">
            <a href="dashboard.html" class="navbar-brand">
                <div class="logo-icon">HT</div>
                Hi-Tech LMS
            </a>
            <nav>
                <ul class="navbar-nav">${navHTML}</ul>
            </nav>
            <div class="navbar-right">
                <button class="notif-btn" title="Notifications">
                    🔔
                    <span class="notif-badge hidden" id="notif-count">0</span>
                </button>
                <div class="user-menu">
                    <button class="user-menu-btn" id="user-menu-btn">
                        <div class="user-avatar">${initials}</div>
                        <span class="user-menu-name">${user.fullName.split(' ')[0]}</span>
                        <span style="font-size:10px; color:var(--gray-400); margin-top:2px;">▼</span>
                    </button>
                    <div class="dropdown-menu" id="user-dropdown">
                        <div class="dropdown-header">
                            <div class="dropdown-header-info">
                                <div class="dropdown-name">${user.fullName}</div>
                                <div class="dropdown-email">${user.email}</div>
                            </div>
                            <div>
                                <span class="badge badge-${role.toLowerCase()}" style="box-shadow: 0 2px 4px rgba(0,0,0,0.05);">${role}</span>
                            </div>
                        </div>
                        <div class="dropdown-body">
                            <a href="profile.html" class="dropdown-item">
                                <span class="item-icon">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                                </span> 
                                <span class="item-text">My Profile</span>
                            </a>
                            <a href="#" class="dropdown-item">
                                <span class="item-icon">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                                </span> 
                                <span class="item-text">Account Settings</span>
                            </a>
                        </div>
                        <div class="dropdown-divider"></div>
                        <div class="dropdown-body">
                            <div class="dropdown-item danger" onclick="logout()">
                                <span class="item-icon">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                                </span> 
                                <span class="item-text">Logout</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    // Toggle dropdown
    document.getElementById('user-menu-btn')?.addEventListener('click', (e) => {
        e.stopPropagation();
        document.getElementById('user-dropdown')?.classList.toggle('open');
    });
    document.addEventListener('click', () => {
        document.getElementById('user-dropdown')?.classList.remove('open');
    });
}

// ---- Format Helpers ----

function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric'
    });
}

function formatDateTime(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function getRoleBadge(role) {
    return `<span class="badge badge-${role.toLowerCase()}">${role}</span>`;
}

function getStatusBadge(status) {
    const map = {
        'ACTIVE': 'badge-active',
        'INACTIVE': 'badge-inactive',
        'PENDING_VERIFICATION': 'badge-pending'
    };
    const labels = {
        'ACTIVE': 'Active',
        'INACTIVE': 'Inactive',
        'PENDING_VERIFICATION': 'Pending'
    };
    return `<span class="badge ${map[status] || ''}">${labels[status] || status}</span>`;
}

function getInitials(name) {
    return (name || '?').split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}

// ---- Course Status Badge ----
function getCourseStatusBadge(status) {
    const map = {
        'ACTIVE': 'badge-active',
        'DRAFT': 'badge-draft',
        'ARCHIVED': 'badge-inactive'
    };
    const labels = {
        'ACTIVE': 'Active',
        'DRAFT': 'Draft',
        'ARCHIVED': 'Archived'
    };
    return `<span class="badge ${map[status] || 'badge-draft'}">${labels[status] || status}</span>`;
}

// ---- Material Type Icon ----
function getMaterialIcon(type) {
    const icons = {
        'PDF': '📄',
        'PPTX': '📊',
        'VIDEO_LINK': '🎬',
        'EXTERNAL_URL': '🔗'
    };
    return icons[type] || '📎';
}

// ---- Format currency (LKR) ----
function formatCurrency(amount) {
    if (amount === null || amount === undefined) return '—';
    return 'LKR ' + parseFloat(amount).toLocaleString('en-LK', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

// ---- Node type label ----
function getNodeTypeLabel(type) {
    const labels = { WEEK: 'Week', MODULE: 'Module', TOPIC: 'Topic' };
    return labels[type] || type;
}

// =====================================================
// MODULE 3: Finance helpers
// =====================================================

/**
 * Returns a colour-coded badge HTML for a payment status.
 * @param {string} status - 'SUCCESS' | 'FAILED' | 'PENDING'
 */
function getPaymentStatusBadge(status) {
    const map = {
        'SUCCESS': '<span class="badge badge-active">✓ Completed</span>',
        'FAILED': '<span class="badge badge-inactive">✗ Failed</span>',
        'PENDING': '<span class="badge badge-draft">⏳ Pending</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

/**
 * Returns a colour-coded badge HTML for a PaymentIntent status.
 */
function getIntentStatusBadge(status) {
    const map = {
        'PENDING': '<span class="badge badge-draft">Pending</span>',
        'CONFIRMED': '<span class="badge badge-active">Confirmed</span>',
        'FAILED': '<span class="badge badge-inactive">Failed</span>',
        'EXPIRED': '<span class="badge" style="background:#fef3c7;color:#92400e;">Expired</span>',
        'CANCELLED': '<span class="badge" style="background:#f1f5f9;color:#64748b;">Cancelled</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

/**
 * Returns a badge for invoice status.
 */
function getInvoiceStatusBadge(status) {
    const map = {
        'GENERATED': '<span class="badge badge-active">Generated</span>',
        'VOID': '<span class="badge badge-inactive">Void</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

/**
 * Detects card brand from a card number string (first digits only).
 * Used on the checkout form for showing a card logo.
 */
function detectCardBrand(number) {
    if (!number) return '';
    if (number.startsWith('4')) return 'VISA';
    if (number.startsWith('5')) return 'MASTERCARD';
    if (/^3[47]/.test(number)) return 'AMEX';
    return 'CARD';
}

// =====================================================
// MODULE 4: Schedule helpers
// =====================================================

/**
 * Returns a coloured badge for a session status/state.
 */
function getSessionStateBadge(state) {
    const map = {
        'Live': '<span class="badge" style="background:#dcfce7;color:#166534;">🔴 Live</span>',
        'Upcoming': '<span class="badge badge-draft">📅 Upcoming</span>',
        'Ended': '<span class="badge" style="background:#f1f5f9;color:#64748b;">✓ Ended</span>',
        'Cancelled': '<span class="badge badge-inactive">✗ Cancelled</span>',
    };
    return map[state] || `<span class="badge">${state}</span>`;
}

/**
 * Returns a meeting platform icon.
 */
function getMeetingPlatformIcon(platform) {
    if (!platform) return '';
    const icons = {
        'ZOOM': '💻',
        'TEAMS': '💼',
        'GOOGLE_MEET': '🎥',
        'CUSTOM': '🔗',
    };
    return icons[platform] || '🔗';
}

/**
 * Assigns a consistent colour to a courseId for the calendar.
 */
const COURSE_COLORS = ['#00adef'];

function getCourseColor(courseId) {
    return COURSE_COLORS[courseId % COURSE_COLORS.length];
}

// =====================================================
// MODULE 5: Exam helpers
// =====================================================

/**
 * Returns a badge for exam status.
 */
function getExamStatusBadge(status) {
    const map = {
        'DRAFT': '<span class="badge badge-draft">Draft</span>',
        'ACTIVE': '<span class="badge badge-active">Active</span>',
        'DEACTIVATED': '<span class="badge badge-inactive">Deactivated</span>',
        'ARCHIVED': '<span class="badge" style="background:#f1f5f9;color:#64748b;">Archived</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

/**
 * Returns a badge for an exam attempt status.
 */
function getAttemptStatusBadge(status) {
    const map = {
        'NOT_STARTED': '<span class="badge badge-draft">Not Started</span>',
        'IN_PROGRESS': '<span class="badge" style="background:#fef3c7;color:#92400e;">In Progress</span>',
        'SUBMITTED': '<span class="badge badge-active">Submitted</span>',
        'GRADED': '<span class="badge" style="background:#dcfce7;color:#166534;">Graded</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

/**
 * Returns a badge for a grade status.
 */
function getGradeStatusBadge(status) {
    const map = {
        'CORRECT': '<span class="badge badge-active">✓ Correct</span>',
        'INCORRECT': '<span class="badge badge-inactive">✗ Incorrect</span>',
        'PARTIAL': '<span class="badge" style="background:#fef3c7;color:#92400e;">Partial</span>',
        'MANUAL_PENDING': '<span class="badge badge-draft">Pending Review</span>',
        'MANUAL_GRADED': '<span class="badge badge-active">Graded</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

/**
 * Returns a pass/fail badge.
 */
function getPassFailBadge(passed) {
    return passed
        ? '<span class="badge badge-active" style="font-size:14px;padding:6px 16px;">✓ PASSED</span>'
        : '<span class="badge badge-inactive" style="font-size:14px;padding:6px 16px;">✗ FAILED</span>';
}

/**
 * Format a duration in seconds as MM:SS.
 */
function formatCountdown(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

/**
 * Returns the window status label for an exam.
 */
function getWindowStatusBadge(windowStatus, examStatus) {
    if (examStatus !== 'ACTIVE') return getExamStatusBadge(examStatus);
    const map = {
        'NOT_OPEN': '<span class="badge badge-draft">Not Open Yet</span>',
        'OPEN': '<span class="badge badge-active">Open</span>',
        'CLOSED': '<span class="badge badge-inactive">Closed</span>',
    };
    return map[windowStatus] || '<span class="badge">Unknown</span>';
}

// =====================================================
// MODULE 6: Support helpers
// =====================================================

function getTicketStatusBadge(status) {
    const map = {
        'OPEN': '<span class="badge" style="background:#dbeafe;color:#1e40af;">Open</span>',
        'IN_PROGRESS': '<span class="badge" style="background:#fef3c7;color:#92400e;">In Progress</span>',
        'PENDING_STUDENT': '<span class="badge" style="background:#f3f4f6;color:#4b5563;">Pending Student</span>',
        'RESOLVED': '<span class="badge badge-active">Resolved</span>',
        'CLOSED': '<span class="badge badge-inactive">Closed</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

function getPriorityBadge(priority) {
    const map = {
        'LOW': '<span class="badge" style="background:#f3f4f6;color:#6b7280;">Low</span>',
        'MEDIUM': '<span class="badge" style="background:#dbeafe;color:#1e40af;">Medium</span>',
        'HIGH': '<span class="badge" style="background:#fef3c7;color:#92400e;">High</span>',
        'CRITICAL': '<span class="badge" style="background:#fee2e2;color:#7f1d1d;">Critical</span>',
    };
    return map[priority] || `<span class="badge">${priority}</span>`;
}

function getAppointmentStatusBadge(status) {
    const map = {
        'PENDING': '<span class="badge" style="background:#fef3c7;color:#92400e;">Pending</span>',
        'CONFIRMED': '<span class="badge badge-active">Confirmed</span>',
        'CANCELLED': '<span class="badge badge-inactive">Cancelled</span>',
        'COMPLETED': '<span class="badge" style="background:#f3f4f6;color:#4b5563;">Completed</span>',
        'RESCHEDULED': '<span class="badge" style="background:#dbeafe;color:#1e40af;">Rescheduled</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

function getArticleStatusBadge(status) {
    const map = {
        'DRAFT': '<span class="badge badge-draft">Draft</span>',
        'PUBLISHED': '<span class="badge badge-active">Published</span>',
        'RETIRED': '<span class="badge badge-inactive">Retired</span>',
    };
    return map[status] || `<span class="badge">${status}</span>`;
}

function getCourseImage(title) {
    if (!title) return 'default.png';
    const lowerTitle = title.toLowerCase();

    if (lowerTitle.includes('maths') || lowerTitle.includes('mathematics')) {
        return 'maths.png';
    } else if (lowerTitle.includes('science')) {
        return 'science.png';
    } else if (lowerTitle.includes('tech') || lowerTitle.includes('technology')) {
        return 'tech.png';
    } else if (lowerTitle.includes('commerce')) {
        return 'commerce.png';
    } else if (lowerTitle.includes('physics')) {
        return 'physics.png';
    }

    return 'default.png';
}
