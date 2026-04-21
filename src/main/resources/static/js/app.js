// ============================================================
//  MovieRec – Main JavaScript
// ============================================================

// ── Preloader ────────────────────────────────────────────────
(function() {
  var preloader = document.getElementById('preloader');
  if (!preloader) return;

  // Generate preloader particles
  var particleContainer = document.getElementById('preloaderParticles');
  if (particleContainer) {
    for (var i = 0; i < 15; i++) {
      var p = document.createElement('div');
      p.className = 'preloader-particle';
      var size = 2 + Math.random() * 3;
      var isRed = i % 3 !== 0;
      p.style.cssText = [
        'width:' + size + 'px',
        'height:' + size + 'px',
        'left:' + (30 + Math.random() * 40) + '%',
        'top:' + (35 + Math.random() * 30) + '%',
        'background:' + (isRed
          ? 'radial-gradient(circle, #ff1a1a, #8b0000)'
          : 'radial-gradient(circle, #ffffff, #aaaaaa)'),
        'box-shadow:' + (isRed
          ? '0 0 6px rgba(255,26,26,0.6)'
          : '0 0 6px rgba(255,255,255,0.4)'),
        'opacity:0.5',
        'animation-delay:' + (Math.random() * 2) + 's',
        'animation-duration:' + (2 + Math.random() * 2) + 's'
      ].join(';');
      particleContainer.appendChild(p);
    }
  }

  // Dismiss preloader after bar fill animation completes (~3.5s)
  setTimeout(function() {
    preloader.classList.add('exit');
    setTimeout(function() {
      preloader.style.display = 'none';
    }, 950);
  }, 3500);
})();

// ── Samurai Hero Particles ───────────────────────────────────
(function() {
  var container = document.getElementById('samuraiParticles');
  if (!container) return;
  for (var i = 0; i < 18; i++) {
    var p = document.createElement('div');
    p.className = 'samurai-particle';
    var size = 2 + Math.random() * 4;
    var isRed = i % 3 !== 0;
    p.style.cssText = [
      'width:' + size + 'px',
      'height:' + size + 'px',
      'left:' + (10 + Math.random() * 80) + '%',
      'top:' + (10 + Math.random() * 80) + '%',
      'background:' + (isRed
        ? 'radial-gradient(circle, #ff1a1a, #8b0000)'
        : 'radial-gradient(circle, #ffffff, #aaaaaa)'),
      'box-shadow:' + (isRed
        ? '0 0 6px rgba(255,26,26,0.5)'
        : '0 0 6px rgba(255,255,255,0.3)'),
      'opacity:0.4',
      'animation-delay:' + (Math.random() * 3) + 's',
      'animation-duration:' + (2.5 + Math.random() * 2.5) + 's'
    ].join(';');
    container.appendChild(p);
  }
})();

// ── CSRF helpers ─────────────────────────────────────────────
function getCsrfToken() {
  var m = document.querySelector('meta[name="_csrf"]');
  return m ? m.getAttribute('content') : '';
}
function getCsrfHeader() {
  var m = document.querySelector('meta[name="_csrf_header"]');
  return m ? m.getAttribute('content') : 'X-CSRF-TOKEN';
}
function postFetch(url) {
  var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
  var token = getCsrfToken();
  var header = getCsrfHeader();
  if (token) headers[header] = token;
  return fetch(url, { method: 'POST', headers: headers });
}

// ── Star Rating ──────────────────────────────────────────────
function initStarRating(container, movieId, currentRating) {
  var stars = container.querySelectorAll('.star');
  var selected = currentRating || 0;
  function paint(n) {
    stars.forEach(function(s, i) { s.classList.toggle('filled', i < n); });
  }
  paint(selected);
  stars.forEach(function(star, idx) {
    star.addEventListener('mouseenter', function() { paint(idx + 1); });
    star.addEventListener('mouseleave', function() { paint(selected); });
    star.addEventListener('click', async function() {
      selected = idx + 1;
      paint(selected);
      try {
        var res = await postFetch('/api/movies/' + movieId + '/rate?score=' + selected);
        var data = await res.json();
        document.querySelectorAll('.avg-rating').forEach(function(el) { el.textContent = data.average.toFixed(1); });
        document.querySelectorAll('.rating-count').forEach(function(el) { el.textContent = '(' + data.count + ')'; });
        showToast('Rating saved! ⭐', 'success');
      } catch(e) { showToast('Could not save rating', 'error'); }
    });
  });
}

// ── Toggle Watchlist ─────────────────────────────────────────
async function toggleWatchlist(movieId, btn) {
  try {
    var res = await postFetch('/api/movies/' + movieId + '/watchlist');
    var data = await res.json();
    if (data.added) {
      btn.textContent = '❤️ In Watchlist';
      btn.classList.add('in-list');
      showToast('Added to Watchlist ❤️', 'success');
    } else {
      btn.textContent = '🤍 Add to Watchlist';
      btn.classList.remove('in-list');
      showToast('Removed from Watchlist', 'info');
    }
  } catch(e) { showToast('Error updating watchlist', 'error'); }
}

// ── Mark as Watched ──────────────────────────────────────────
async function markWatched(movieId, btn) {
  try {
    await postFetch('/api/movies/' + movieId + '/watch');
    btn.textContent = '✅ Watched';
    btn.disabled = true;
    btn.className = 'btn btn-success';
    showToast('Marked as watched! 🎬', 'success');
  } catch(e) { showToast('Error', 'error'); }
}

// ── Toast notifications ──────────────────────────────────────
function showToast(message, type) {
  type = type || 'info';
  var container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }
  var toast = document.createElement('div');
  var colors = { success: '#22c55e', error: '#ef4444', info: '#3b82f6', warning: '#f59e0b' };
  var color = colors[type] || colors.info;
  toast.style.cssText = [
    'background:' + color,
    'color:#fff',
    'padding:12px 20px',
    'border-radius:10px',
    'font-size:.875rem',
    'font-weight:600',
    'box-shadow:0 4px 20px rgba(0,0,0,.35)',
    'animation:toast-in .25s ease both',
    'max-width:300px',
    'pointer-events:none',
    'font-family:inherit'
  ].join(';');
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(function() {
    toast.style.transition = 'opacity .3s, transform .3s';
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    setTimeout(function() { toast.remove(); }, 320);
  }, 2800);
}

// ── Navbar scroll effect ─────────────────────────────────────
(function() {
  var navbar = document.querySelector('.navbar');
  if (!navbar) return;
  function onScroll() {
    navbar.classList.toggle('scrolled', window.scrollY > 40);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
})();

// ── IntersectionObserver — scroll-reveal for movie cards ─────
(function() {
  if (!('IntersectionObserver' in window)) {
    // Fallback: just make everything visible
    document.querySelectorAll('.movie-card, .reveal').forEach(function(el) {
      el.classList.add('is-visible');
    });
    return;
  }

  // Movie cards — staggered reveal
  var cardObserver = new IntersectionObserver(function(entries) {
    entries.forEach(function(entry) {
      if (entry.isIntersecting) {
        var el = entry.target;
        // Small delay based on sibling index for stagger
        var siblings = Array.from(el.parentElement ? el.parentElement.children : [el]);
        var idx = siblings.indexOf(el);
        var delay = Math.min(idx * 40, 400); // cap at 400ms
        setTimeout(function() { el.classList.add('is-visible'); }, delay);
        cardObserver.unobserve(el);
      }
    });
  }, { rootMargin: '0px 0px -40px 0px', threshold: 0.05 });

  function observeCards() {
    document.querySelectorAll('.movie-card:not(.is-visible)').forEach(function(card) {
      cardObserver.observe(card);
    });
  }

  // Section / generic reveal
  var sectionObserver = new IntersectionObserver(function(entries) {
    entries.forEach(function(entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
        sectionObserver.unobserve(entry.target);
      }
    });
  }, { rootMargin: '0px 0px -60px 0px', threshold: 0.1 });

  function observeSections() {
    document.querySelectorAll('.reveal:not(.is-visible)').forEach(function(el) {
      sectionObserver.observe(el);
    });
  }

  document.addEventListener('DOMContentLoaded', function() {
    observeCards();
    observeSections();
  });

  // Re-observe if new cards get added dynamically
  if ('MutationObserver' in window) {
    var mutObs = new MutationObserver(function() {
      observeCards();
      observeSections();
    });
    document.addEventListener('DOMContentLoaded', function() {
      mutObs.observe(document.body, { childList: true, subtree: true });
    });
  }
})();

// ── Keyboard nav: Enter key on movie cards ───────────────────
document.addEventListener('keydown', function(e) {
  if (e.key === 'Enter' && e.target.classList.contains('movie-card')) {
    e.target.click();
  }
});
