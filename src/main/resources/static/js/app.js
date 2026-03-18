// ============================================================
//  MovieRec – Main JavaScript
// ============================================================

// Lấy CSRF token từ meta tag
function getCsrfToken() {
  var meta = document.querySelector('meta[name="_csrf"]');
  return meta ? meta.getAttribute('content') : '';
}
function getCsrfHeader() {
  var meta = document.querySelector('meta[name="_csrf_header"]');
  return meta ? meta.getAttribute('content') : 'X-CSRF-TOKEN';
}

// POST helper tự động đính CSRF
function postFetch(url) {
  var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
  var token = getCsrfToken();
  var header = getCsrfHeader();
  if (token) headers[header] = token;
  return fetch(url, { method: 'POST', headers: headers });
}

// ── Star Rating Widget ────────────────────────────────────────
function initStarRating(container, movieId, currentRating) {
  const stars = container.querySelectorAll('.star');
  let selected = currentRating || 0;

  function paint(n) {
    stars.forEach((s, i) => {
      s.classList.toggle('filled', i < n);
    });
  }

  paint(selected);

  stars.forEach((star, idx) => {
    star.addEventListener('mouseenter', () => paint(idx + 1));
    star.addEventListener('mouseleave', () => paint(selected));
    star.addEventListener('click', async () => {
      selected = idx + 1;
      paint(selected);
      try {
        const res = await postFetch(`/api/movies/${movieId}/rate?score=${selected}`);
        const data = await res.json();
        document.querySelectorAll('.avg-rating').forEach(el => el.textContent = data.average.toFixed(1));
        document.querySelectorAll('.rating-count').forEach(el => el.textContent = `(${data.count})`);
        showToast('Rating saved! ⭐', 'success');
      } catch (e) { showToast('Could not save rating', 'error'); }
    });
  });
}

// ── Toggle Watchlist ──────────────────────────────────────────
async function toggleWatchlist(movieId, btn) {
  try {
    const res = await postFetch(`/api/movies/${movieId}/watchlist`);
    const data = await res.json();
    if (data.added) {
      btn.textContent = '❤️ In Watchlist';
      btn.classList.add('in-list');
      showToast('Added to Watchlist ❤️', 'success');
    } else {
      btn.textContent = '🤍 Add to Watchlist';
      btn.classList.remove('in-list');
      showToast('Removed from Watchlist', 'info');
    }
  } catch (e) { showToast('Error updating watchlist', 'error'); }
}

// ── Mark as Watched ──────────────────────────────────────────
async function markWatched(movieId, btn) {
  try {
    await postFetch(`/api/movies/${movieId}/watch`);
    btn.textContent = '✅ Watched';
    btn.disabled = true;
    btn.className = 'btn btn-success';
    showToast('Marked as watched! 🎬', 'success');
  } catch (e) { showToast('Error', 'error'); }
}

// ── Submit Comment ───────────────────────────────────────────
async function submitComment(movieId, input) {
  const text = typeof input === 'string' ? input : input.value;
  if (!text || !text.trim()) return;
  try {
    const res = await postFetch(`/api/movies/${movieId}/comment?text=${encodeURIComponent(text.trim())}`);
    const data = await res.json();
    const list = document.getElementById('comment-list');
    const empty = list.querySelector('.empty-state');
    if (empty) empty.remove();
    const now = new Date();
    const dateStr = now.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' })
                  + ' ' + now.toTimeString().slice(0,5);
    list.insertAdjacentHTML('afterbegin', `
      <div class="comment-box">
        <div class="comment-header">
          <span class="comment-author">👤 ${data.username}</span>
          <span class="comment-date">${dateStr}</span>
        </div>
        <p class="comment-text">${data.text}</p>
      </div>`);
    showToast('Comment posted! 💬', 'success');
  } catch (e) { showToast('Could not post comment', 'error'); }
}

// ── Toast notifications ──────────────────────────────────────
function showToast(message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.style.cssText = 'position:fixed;bottom:2rem;right:2rem;z-index:99999;display:flex;flex-direction:column;gap:.5rem';
    document.body.appendChild(container);
  }
  const toast = document.createElement('div');
  const colors = { success: '#22c55e', error: '#ef4444', info: '#3b82f6', warning: '#f59e0b' };
  toast.style.cssText = `background:${colors[type]||colors.info};color:#fff;padding:.75rem 1.25rem;border-radius:8px;
    font-size:.875rem;font-weight:500;box-shadow:0 4px 12px rgba(0,0,0,.3);
    animation:slideIn .2s ease;max-width:300px`;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => { toast.style.opacity = '0'; toast.style.transition = 'opacity .3s';
    setTimeout(() => toast.remove(), 300); }, 3000);
}

const style = document.createElement('style');
style.textContent = '@keyframes slideIn{from{transform:translateX(100%);opacity:0}to{transform:translateX(0);opacity:1}}';
document.head.appendChild(style);

// ── Navbar scroll effect ─────────────────────────────────────
(function() {
  var navbar = document.querySelector('.navbar');
  if (!navbar) return;
  function onScroll() {
    if (window.scrollY > 50) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
})();

// ── Staggered card animations ────────────────────────────────
(function() {
  document.addEventListener('DOMContentLoaded', function() {
    var cards = document.querySelectorAll('.movie-card');
    cards.forEach(function(card, i) {
      card.style.animationDelay = (i * 0.05) + 's';
    });
    var statCards = document.querySelectorAll('.stat-card');
    statCards.forEach(function(card, i) {
      card.style.animationDelay = (i * 0.08) + 's';
    });
  });
})();
