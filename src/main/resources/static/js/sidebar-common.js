(function(window) {
    const ROLE_ADMIN = 'ADMIN';
    const ROLE_OPERATOR = 'OPERATOR';
    const ROLE_READ_ONLY = 'READ_ONLY';

    function normalizeRole(roleValue) {
        const normalized = String(roleValue || '').replace(/^ROLE_/, '').toUpperCase();
        if (normalized === ROLE_ADMIN || normalized === ROLE_OPERATOR || normalized === ROLE_READ_ONLY) {
            return normalized;
        }
        return ROLE_READ_ONLY;
    }

    function formatRoleLabel(roleValue) {
        const role = normalizeRole(roleValue);
        const labels = {
            ADMIN: 'Admin',
            OPERATOR: 'Operator',
            READ_ONLY: 'Read Only'
        };
        return labels[role] || role.replace(/_/g, ' ');
    }

    function applyMenuOpenState(isOpen) {
        const toggle = document.getElementById('sidebarSettingsToggle');
        const chip = document.getElementById('sidebarUserChip');

        if (toggle) {
            toggle.classList.toggle('active', isOpen);
            toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        }

        if (chip) {
            chip.classList.toggle('active', isOpen);
            chip.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        }
    }

    function toggleSidebarUserMenu() {
        const menu = document.getElementById('sidebarSettingsMenu');
        if (!menu) return;

        const isOpen = menu.classList.toggle('open');
        applyMenuOpenState(isOpen);
    }

    function closeSidebarUserMenu() {
        const menu = document.getElementById('sidebarSettingsMenu');
        if (!menu) return;

        menu.classList.remove('open');
        applyMenuOpenState(false);
    }

    function isOutsideSidebarSettingsMenuClick(event) {
        const menu = document.getElementById('sidebarSettingsMenu');
        if (!menu) {
            return false;
        }

        const toggle = document.getElementById('sidebarSettingsToggle');
        const chip = document.getElementById('sidebarUserChip');
        const target = event && event.target;

        const clickedInsideMenu = menu.contains(target);
        const clickedToggle = toggle && (target === toggle || toggle.contains(target));
        const clickedChip = chip && (target === chip || chip.contains(target));

        return !clickedInsideMenu && !clickedToggle && !clickedChip;
    }

    function updateSidebarSettingsMenuVisibility(roleValue) {
        const role = normalizeRole(roleValue);
        const userManagementItem = document.getElementById('sidebarUserManagementMenuItem');
        const smtpSettingsItem = document.getElementById('sidebarSmtpSettingsMenuItem');
        if (userManagementItem) {
            userManagementItem.style.display = role === ROLE_ADMIN ? 'block' : 'none';
        }
        if (smtpSettingsItem) {
            smtpSettingsItem.style.display = role === ROLE_ADMIN ? 'block' : 'none';
        }
    }

    function setSidebarCurrentUsername(username) {
        const sidebarUsername = document.getElementById('sidebarCurrentUsername');
        if (sidebarUsername) {
            sidebarUsername.textContent = username || 'User';
        }
    }

    window.ReportServerSidebar = {
        ROLE_ADMIN,
        ROLE_OPERATOR,
        ROLE_READ_ONLY,
        normalizeRole,
        formatRoleLabel,
        toggleSidebarUserMenu,
        closeSidebarUserMenu,
        isOutsideSidebarSettingsMenuClick,
        updateSidebarSettingsMenuVisibility,
        setSidebarCurrentUsername
    };
})(window);
