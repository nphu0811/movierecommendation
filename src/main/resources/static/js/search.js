/**
 * Search Page Autocomplete, Suggestions & Click Tracking
 */
(function() {
    const input = document.getElementById('mainSearchInput');
    const autocomplete = document.getElementById('searchAutocomplete');
    const resultsContainer = document.getElementById('autocompleteResults');
    const clearBtn = document.getElementById('searchClearBtn');
    const initialTrends = document.getElementById('searchInitialTrends');
    const searchForm = input.closest('form');
    const metadataEl = document.getElementById('search-metadata');
    const searchId = metadataEl ? metadataEl.getAttribute('data-search-id') : null;
    
    if (!input || !autocomplete) return;

    let timer = null;
    let focusedIndex = -1;

    // Show/Hide Clear Button
    const toggleClearBtn = () => {
        if (input.value.length > 0) {
            clearBtn.classList.add('visible');
            if (initialTrends) initialTrends.classList.add('hidden');
        } else {
            clearBtn.classList.remove('visible');
            autocomplete.classList.remove('active');
            if (initialTrends) initialTrends.classList.remove('hidden');
        }
    };

    input.addEventListener('input', function() {
        toggleClearBtn();
        const q = this.value.trim();
        
        clearTimeout(timer);
        if (q.length < 1) {
            autocomplete.classList.remove('active');
            return;
        }

        timer = setTimeout(() => {
            fetchAutocompleteAndSuggestions(q);
        }, 250);
    });

    clearBtn.addEventListener('click', () => {
        input.value = '';
        input.focus();
        toggleClearBtn();
    });

    if (searchForm) {
        searchForm.addEventListener('submit', (e) => {
            const q = input.value.trim();
            if (!q) {
                e.preventDefault();
                input.focus();
                return;
            }
            input.value = q;
        });
    }

    input.addEventListener('keydown', function(e) {
        const items = resultsContainer.querySelectorAll('.autocomplete-item');
        
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            focusedIndex = (focusedIndex + 1) % items.length;
            updateFocus(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            focusedIndex = (focusedIndex - 1 + items.length) % items.length;
            updateFocus(items);
        } else if (e.key === 'Enter') {
            if (focusedIndex > -1 && items[focusedIndex]) {
                e.preventDefault();
                items[focusedIndex].click();
            } else if (this.value.trim()) {
                window.location.href = '/search?source=search_page&q=' + encodeURIComponent(this.value.trim());
            }
        } else if (e.key === 'Escape') {
            autocomplete.classList.remove('active');
        }
    });

    const updateFocus = (items) => {
        items.forEach((item, index) => {
            if (index === focusedIndex) {
                item.classList.add('focused');
                item.scrollIntoView({ block: 'nearest' });
            } else {
                item.classList.remove('focused');
            }
        });
    };

    const fetchAutocompleteAndSuggestions = (q) => {
        Promise.all([
            fetch('/api/search/suggestions?prefix=' + encodeURIComponent(q)).then(res => res.json()).catch(() => []),
            fetch('/api/search/autocomplete?q=' + encodeURIComponent(q)).then(res => res.json()).catch(() => [])
        ]).then(([suggestions, movies]) => {
            renderCombinedResults(suggestions, movies, q);
        }).catch(err => console.error('Error fetching combined results:', err));
    };

    const renderCombinedResults = (suggestions, movies, q) => {
        if ((!suggestions || suggestions.length === 0) && (!movies || movies.length === 0)) {
            autocomplete.classList.remove('active');
            return;
        }

        focusedIndex = -1;
        let html = '';

        // 1. Search Suggestions Section (from Search History)
        if (suggestions && suggestions.length > 0) {
            html += `<div class="autocomplete-section-header" style="padding: 10px 16px 4px; font-size: 0.75rem; font-weight: bold; color: var(--red); text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.8;">Search Suggestions</div>`;
            suggestions.forEach(s => {
                html += `
                    <div class="autocomplete-item suggestion-item" data-query="${esc(s.query)}" style="display:flex; align-items:center; padding: 10px 16px; cursor: pointer;">
                        <i class="ph ph-magnifying-glass" style="color: #888; font-size: 1.1rem; margin-right: 16px;"></i>
                        <div class="autocomplete-info" style="flex: 1;">
                            <div class="autocomplete-title" style="font-size: 0.95rem; font-weight: normal; color: var(--white);">${highlight(esc(s.query), esc(q))}</div>
                        </div>
                    </div>
                `;
            });
        }

        // 2. Matching Movies Section (from Autocomplete)
        if (movies && movies.length > 0) {
            html += `<div class="autocomplete-section-header" style="padding: 14px 16px 4px; font-size: 0.75rem; font-weight: bold; color: var(--red); text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.8;">Matching Movies</div>`;
            movies.forEach(m => {
                const posterUrl = m.poster ? m.poster : 'data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2230%22 height=%2245%22 viewBox=%220 0 30 45%22><rect width=%22100%%22 height=%22100%%22 fill=%22%23222%22/></svg>';
                html += `
                    <div class="autocomplete-item movie-autocomplete-item" data-movie-id="${m.id}" onclick="window.location.href='/movies/${m.id}'" style="display:flex; align-items:center; padding: 10px 16px; cursor: pointer;">
                        <img src="${posterUrl}" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2230%22 height=%2245%22 viewBox=%220 0 30 45%22><rect width=%22100%%22 height=%22100%%22 fill=%22%23222%22/></svg>'" style="width: 30px; height: 45px; object-fit: cover; border-radius: 4px; margin-right: 16px; background: #222;" />
                        <div class="autocomplete-info" style="flex: 1;">
                            <div class="autocomplete-title" style="font-size: 0.95rem; font-weight: normal; color: var(--white);">${highlight(esc(m.title), esc(q))}</div>
                            <div class="autocomplete-meta" style="font-size: 0.8rem; color: #888; margin-top: 2px;">${esc(m.year)} • ${esc(m.genres ? m.genres.join(', ') : '')}</div>
                        </div>
                    </div>
                `;
            });
        }

        resultsContainer.innerHTML = html;
        autocomplete.classList.add('active');

        // Attach click listeners to suggestion items so they execute a real search
        resultsContainer.querySelectorAll('.suggestion-item').forEach(item => {
            item.addEventListener('click', function(e) {
                const query = this.getAttribute('data-query');
                if (query) {
                    window.location.href = '/search?source=suggestion&q=' + encodeURIComponent(query);
                }
            });
        });
    };

    const highlight = (text, q) => {
        const idx = text.toLowerCase().indexOf(q.toLowerCase());
        if (idx < 0) return text;
        return text.substring(0, idx) + `<span class="autocomplete-highlight">${text.substring(idx, idx + q.length)}</span>` + text.substring(idx + q.length);
    };

    const esc = (s) => {
        if (!s) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    };

    // Close when clicking outside
    document.addEventListener('click', (e) => {
        if (!input.contains(e.target) && !autocomplete.contains(e.target)) {
            autocomplete.classList.remove('active');
        }
    });

    // --- Search Click Tracking ---
    if (searchId) {
        document.querySelectorAll('.search-result-card-wrapper').forEach(wrapper => {
            const movieId = wrapper.getAttribute('data-movie-id');
            const card = wrapper.querySelector('.movie-card');
            if (card && movieId) {
                card.addEventListener('click', function(e) {
                    fetch(`/api/search-history/${searchId}/click`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ movieId: parseInt(movieId) }),
                        keepalive: true
                    }).catch(err => console.warn('Failed to track click:', err));
                });
            }
        });
    }

    // Initial clear button check
    toggleClearBtn();

})();

/**
 * Row Scroll Arrow Navigation
 */
(function() {
    const SCROLL_AMOUNT = 600; // px to scroll per click

    document.querySelectorAll('.row-scroll-wrapper').forEach(wrapper => {
        const row = wrapper.querySelector('.movies-row');
        const leftBtn = wrapper.querySelector('.row-arrow--left');
        const rightBtn = wrapper.querySelector('.row-arrow--right');

        if (!row || !leftBtn || !rightBtn) return;

        function updateArrows() {
            const atStart = row.scrollLeft <= 4;
            const atEnd = row.scrollLeft + row.clientWidth >= row.scrollWidth - 4;

            leftBtn.classList.toggle('hidden', atStart);
            rightBtn.classList.toggle('hidden', atEnd);
        }

        leftBtn.addEventListener('click', () => {
            row.scrollBy({ left: -SCROLL_AMOUNT, behavior: 'smooth' });
        });

        rightBtn.addEventListener('click', () => {
            row.scrollBy({ left: SCROLL_AMOUNT, behavior: 'smooth' });
        });

        row.addEventListener('scroll', updateArrows, { passive: true });
        window.addEventListener('resize', updateArrows, { passive: true });

        // Initial check
        updateArrows();
        // Re-check after cards become visible (they start with opacity:0)
        setTimeout(updateArrows, 500);
    });
})();
