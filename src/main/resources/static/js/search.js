/**
 * Search Page Autocomplete & Interaction
 */
(function() {
    const input = document.getElementById('mainSearchInput');
    const autocomplete = document.getElementById('searchAutocomplete');
    const resultsContainer = document.getElementById('autocompleteResults');
    const clearBtn = document.getElementById('searchClearBtn');
    
    if (!input || !autocomplete) return;

    let timer = null;
    let focusedIndex = -1;

    // Show/Hide Clear Button
    const toggleClearBtn = () => {
        if (input.value.length > 0) {
            clearBtn.classList.add('visible');
        } else {
            clearBtn.classList.remove('visible');
            autocomplete.classList.remove('active');
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
            fetchAutocomplete(q);
        }, 250);
    });

    clearBtn.addEventListener('click', () => {
        input.value = '';
        input.focus();
        toggleClearBtn();
    });

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
                window.location.href = '/search?q=' + encodeURIComponent(this.value.trim());
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

    const fetchAutocomplete = (q) => {
        fetch('/api/search/autocomplete?q=' + encodeURIComponent(q))
            .then(res => res.json())
            .then(data => {
                renderAutocomplete(data, q);
            })
            .catch(err => console.error('Autocomplete error:', err));
    };

    const renderAutocomplete = (movies, q) => {
        if (!movies || movies.length === 0) {
            autocomplete.classList.remove('active');
            return;
        }

        focusedIndex = -1;
        let html = '';
        movies.forEach(m => {
            const poster = m.poster 
                ? `<img src="${esc(m.poster)}" class="autocomplete-poster" onerror="this.src='/img/no-poster.jpg'">`
                : `<div class="autocomplete-poster" style="display:flex;align-items:center;justify-content:center;background:#1a1a1a"><i class="ph ph-film-strip"></i></div>`;
            
            const genres = m.genres ? m.genres.slice(0, 2).join(' · ') : '';
            const meta = [m.year, genres].filter(Boolean).join(' • ');

            html += `
                <div class="autocomplete-item" data-id="${m.id}" onclick="window.location.href='/movies/${m.id}'">
                    ${poster}
                    <div class="autocomplete-info">
                        <div class="autocomplete-title">${highlight(esc(m.title), esc(q))}</div>
                        <div class="autocomplete-meta">${esc(meta)}</div>
                    </div>
                </div>
            `;
        });

        resultsContainer.innerHTML = html;
        autocomplete.classList.add('active');
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

    // Initial clear button check
    toggleClearBtn();

})();
