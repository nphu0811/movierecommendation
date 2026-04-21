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



// ── CSRF helpers ─────────────────────────────────────────────
// Hero/Auth particle backdrop
(function() {
  var particleHost = document.querySelector('.hero-section, .auth-container');
  var canvas = document.getElementById('heroParticlesCanvas') || document.getElementById('authParticlesCanvas');
  if (!particleHost || !canvas) return;

  var ctx = canvas.getContext('2d');
  if (!ctx) return;

  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var finePointer = window.matchMedia && window.matchMedia('(pointer: fine)').matches;
  var isAuthSurface = particleHost.classList.contains('auth-container');
  var particles = [];
  var ripples = [];
  var pointer = {
    x: 0,
    y: 0,
    active: false,
    radius: reduceMotion || !finePointer ? 0 : (isAuthSurface ? 120 : 150)
  };
  var width = 0;
  var height = 0;
  var frameId = 0;
  var lastRippleAt = 0;

  function between(min, max) {
    return min + Math.random() * (max - min);
  }

  function createParticle() {
    var isRed = Math.random() < 0.72;
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      vx: between(-0.18, 0.18),
      vy: between(-0.14, 0.14),
      size: isRed ? between(1.4, 3.6) : between(1.1, 2.8),
      alpha: isRed ? between(0.45, 0.92) : between(0.22, 0.58),
      glow: isRed ? between(10, 18) : between(6, 12),
      color: isRed ? '229, 9, 20' : '255, 255, 255',
      drift: between(0.0006, 0.0016),
      phase: Math.random() * Math.PI * 2
    };
  }

  function particleCount() {
    var density = finePointer ? (isAuthSurface ? 22 : 18) : (isAuthSurface ? 32 : 28);
    var maxCount = finePointer ? (isAuthSurface ? 62 : 84) : (isAuthSurface ? 34 : 42);
    var minCount = finePointer ? (isAuthSurface ? 28 : 42) : (isAuthSurface ? 16 : 24);
    var count = Math.round(width / density);
    if (reduceMotion) count = Math.round(count * 0.65);
    return Math.max(minCount, Math.min(maxCount, count));
  }

  function resizeCanvas() {
    var rect = particleHost.getBoundingClientRect();
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = Math.max(1, rect.width);
    height = Math.max(1, rect.height);

    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    var targetCount = particleCount();
    if (particles.length > targetCount) particles.length = targetCount;
    while (particles.length < targetCount) particles.push(createParticle());
  }

  function wrapParticle(particle) {
    if (particle.x < -20) particle.x = width + 20;
    if (particle.x > width + 20) particle.x = -20;
    if (particle.y < -20) particle.y = height + 20;
    if (particle.y > height + 20) particle.y = -20;
  }

  function spawnRipple(x, y, strength) {
    ripples.push({
      x: x,
      y: y,
      radius: strength > 1 ? 12 : 6,
      maxRadius: strength > 1 ? 118 : 84,
      alpha: strength > 1 ? 0.2 : 0.12,
      speed: strength > 1 ? 2.8 : 2.15,
      fillAlpha: strength > 1 ? 0.085 : 0.045
    });
    if (ripples.length > 16) ripples.shift();
  }

  function updatePointer(event) {
    var rect = particleHost.getBoundingClientRect();
    pointer.x = event.clientX - rect.left;
    pointer.y = event.clientY - rect.top;
    pointer.active = true;

    if (pointer.radius === 0) return;

    var now = performance.now();
    if (now - lastRippleAt > 72) {
      spawnRipple(pointer.x, pointer.y, 1);
      lastRippleAt = now;
    }
  }

  function fadePointer() {
    pointer.active = false;
  }

  function drawPointerBloom() {
    if (!pointer.active || pointer.radius === 0) return;

    var glowRadius = pointer.radius * 1.2;
    var gradient = ctx.createRadialGradient(
      pointer.x,
      pointer.y,
      0,
      pointer.x,
      pointer.y,
      glowRadius
    );
    gradient.addColorStop(0, 'rgba(255, 255, 255, 0.08)');
    gradient.addColorStop(0.24, 'rgba(229, 9, 20, 0.08)');
    gradient.addColorStop(0.62, 'rgba(229, 9, 20, 0.025)');
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');

    ctx.save();
    ctx.fillStyle = gradient;
    ctx.fillRect(pointer.x - glowRadius, pointer.y - glowRadius, glowRadius * 2, glowRadius * 2);
    ctx.restore();
  }

  function drawRipples() {
    for (var i = ripples.length - 1; i >= 0; i--) {
      var ripple = ripples[i];
      ripple.radius += ripple.speed;
      ripple.alpha *= 0.965;
      ripple.fillAlpha *= 0.955;

      if (ripple.radius >= ripple.maxRadius || ripple.alpha < 0.008) {
        ripples.splice(i, 1);
        continue;
      }

      ctx.save();
      ctx.lineWidth = 1.2;
      ctx.strokeStyle = 'rgba(255, 255, 255, ' + ripple.alpha + ')';
      ctx.beginPath();
      ctx.arc(ripple.x, ripple.y, ripple.radius, 0, Math.PI * 2);
      ctx.stroke();

      ctx.fillStyle = 'rgba(229, 9, 20, ' + ripple.fillAlpha + ')';
      ctx.beginPath();
      ctx.arc(ripple.x, ripple.y, ripple.radius * 0.62, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }
  }

  function drawParticle(particle) {
    ctx.save();
    ctx.fillStyle = 'rgba(' + particle.color + ', ' + particle.alpha + ')';
    ctx.shadowBlur = particle.glow;
    ctx.shadowColor = 'rgba(' + particle.color + ', 0.45)';
    ctx.beginPath();
    ctx.arc(particle.x, particle.y, particle.size, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function updateParticle(particle, time) {
    var floatX = Math.cos(time * particle.drift + particle.phase) * 0.015;
    var floatY = Math.sin(time * particle.drift * 0.86 + particle.phase) * 0.015;

    particle.vx += floatX;
    particle.vy += floatY;

    if (pointer.active && pointer.radius > 0) {
      var dx = particle.x - pointer.x;
      var dy = particle.y - pointer.y;
      var dist = Math.sqrt(dx * dx + dy * dy) || 1;
      if (dist < pointer.radius) {
        var force = (1 - dist / pointer.radius) * 0.55;
        particle.vx += (dx / dist) * force;
        particle.vy += (dy / dist) * force;
      }
    }

    particle.vx *= 0.985;
    particle.vy *= 0.985;
    particle.x += particle.vx;
    particle.y += particle.vy;

    wrapParticle(particle);
    drawParticle(particle);
  }

  function render(time) {
    ctx.clearRect(0, 0, width, height);
    drawPointerBloom();
    drawRipples();

    for (var i = 0; i < particles.length; i++) {
      updateParticle(particles[i], time);
    }

    frameId = window.requestAnimationFrame(render);
  }

  particleHost.addEventListener('pointerenter', updatePointer);
  particleHost.addEventListener('pointermove', updatePointer);
  particleHost.addEventListener('pointerleave', fadePointer);
  particleHost.addEventListener('pointerdown', function(event) {
    updatePointer(event);
    if (pointer.radius > 0) spawnRipple(pointer.x, pointer.y, 2);
  });

  window.addEventListener('resize', resizeCanvas, { passive: true });
  document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
      window.cancelAnimationFrame(frameId);
      frameId = 0;
      return;
    }
    if (!frameId) frameId = window.requestAnimationFrame(render);
  });

  resizeCanvas();
  frameId = window.requestAnimationFrame(render);
})();

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

// -- IntersectionObserver - bidirectional reveal/hide ----------
(function() {
  var revealSelector = '.movie-card, .reveal';

  function prepareCardDelay(card) {
    if (card.dataset.revealDelay) return;
    var parent = card.parentElement;
    var idx = 0;
    if (parent) {
      var cards = parent.querySelectorAll('.movie-card');
      idx = Array.prototype.indexOf.call(cards, card);
      if (idx < 0) idx = 0;
    }
    var delay = Math.min(idx * 40, 400);
    card.dataset.revealDelay = String(delay);
  }

  if (!('IntersectionObserver' in window)) {
    document.querySelectorAll(revealSelector).forEach(function(el) {
      el.classList.add('is-visible');
    });
    return;
  }

  var revealObserver = new IntersectionObserver(function(entries) {
    entries.forEach(function(entry) {
      var el = entry.target;
      var isVisible = entry.isIntersecting && entry.intersectionRatio > 0;

      if (el.classList.contains('movie-card')) {
        var delay = el.dataset.revealDelay || '0';
        el.style.transitionDelay = isVisible ? (delay + 'ms') : '0ms';
      }

      el.classList.toggle('is-visible', isVisible);
    });
  }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });

  function observeRevealTargets() {
    document.querySelectorAll(revealSelector).forEach(function(el) {
      if (el.dataset.revealObserved === '1') return;
      if (el.classList.contains('movie-card')) prepareCardDelay(el);
      revealObserver.observe(el);
      el.dataset.revealObserved = '1';
    });
  }

  document.addEventListener('DOMContentLoaded', observeRevealTargets);
  observeRevealTargets();

  if ('MutationObserver' in window) {
    var mutObs = new MutationObserver(observeRevealTargets);
    document.addEventListener('DOMContentLoaded', function() {
      mutObs.observe(document.body, { childList: true, subtree: true });
    });
  }
})();

// -- Keyboard nav: Enter key on movie cards -------------------

document.addEventListener('keydown', function(e) {
  if (e.key === 'Enter' && e.target.classList.contains('movie-card')) {
    e.target.click();
  }
});
