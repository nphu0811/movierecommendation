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

  // Dismiss preloader quickly (~1s)
  setTimeout(function() {
    preloader.classList.add('exit');
    preloader.style.pointerEvents = 'none';
    setTimeout(function() {
      preloader.style.display = 'none';
    }, 800);
  }, 1000);
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
  
  var observer = new IntersectionObserver(function(entries) {
    entries.forEach(function(entry) {
      if (entry.isIntersecting) {
        if (!frameId) frameId = window.requestAnimationFrame(render);
      } else {
        window.cancelAnimationFrame(frameId);
        frameId = 0;
      }
    });
  }, { threshold: 0.1 });
  observer.observe(canvas);
})();

// GSAP page choreography
(function() {
  var GSAP_CDN = 'https://cdn.jsdelivr.net/npm/gsap@3/dist/gsap.min.js';

  function ready(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
      return;
    }
    fn();
  }

  function loadGsap(callback) {
    if (window.gsap) {
      callback(window.gsap);
      return;
    }

    var existingScript = document.querySelector('script[data-movierec-gsap]');
    if (existingScript) {
      document.addEventListener('movierec:gsap-ready', function() {
        if (window.gsap) callback(window.gsap);
      }, { once: true });
      return;
    }

    var script = document.createElement('script');
    script.src = GSAP_CDN;
    script.defer = true;
    script.dataset.movierecGsap = 'true';
    script.onload = function() {
      document.dispatchEvent(new Event('movierec:gsap-ready'));
      if (window.gsap) callback(window.gsap);
    };
    script.onerror = function() {
      document.documentElement.classList.add('gsap-unavailable');
    };
    document.head.appendChild(script);
  }

  function startGsapChoreography(gsap) {
    if (!gsap) return;

  var reduceMotion = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function toArray(collection) {
    return Array.prototype.slice.call(collection || []);
  }

  function compact(items) {
    return items.filter(function(item) { return !!item; });
  }

  function isVisible(el) {
    return el && window.getComputedStyle(el).display !== 'none';
  }

  function hasTargets(targets) {
    return targets && targets.length;
  }

  function revealTargets(targets, options) {
    targets = compact(toArray(targets)).filter(isVisible);
    if (!hasTargets(targets)) return;

    if (reduceMotion) {
      gsap.set(targets, {
        autoAlpha: 1,
        x: 0,
        y: 0,
        scale: 1,
        clearProps: 'transform,opacity,visibility'
      });
      return;
    }

    options = options || {};
    gsap.fromTo(targets, {
      autoAlpha: 0,
      x: options.x || 0,
      y: options.y == null ? 16 : options.y,
      scale: options.scale || 1
    }, {
      autoAlpha: 1,
      x: 0,
      y: 0,
      scale: 1,
      duration: options.duration || 0.44,
      delay: options.delay || 0,
      stagger: options.stagger == null ? 0.055 : options.stagger,
      ease: options.ease || 'power2.out',
      overwrite: 'auto',
      clearProps: options.clearProps || 'transform,opacity,visibility'
    });
  }

  gsap.defaults({
    ease: 'power2.out',
    duration: 0.42,
    overwrite: 'auto'
  });

  function initAuthGsap() {
    var auth = document.querySelector('.auth-container');
    if (!auth) return;

    var showcase = auth.querySelector('.auth-showcase');
    var card = auth.querySelector('.auth-card');
    var logo = auth.querySelector('.auth-logo');
    var formItems = toArray(auth.querySelectorAll('.auth-card .form-group, .auth-card .alert'));
    var featureCards = toArray(auth.querySelectorAll('.auth-feature-card'));
    var actionItems = toArray(auth.querySelectorAll('.auth-submit, .auth-alt-action'));

    if (reduceMotion) {
      gsap.set(compact([showcase, card, logo]).concat(formItems, featureCards, actionItems), {
        autoAlpha: 1,
        x: 0,
        y: 0,
        scale: 1,
        clearProps: 'transform,opacity,visibility'
      });
      return;
    }

    function runIntro(context) {
      var isDesktop = !context || !context.conditions || context.conditions.isDesktop;
      var tl = gsap.timeline({ defaults: { ease: 'power3.out' } });

      tl.fromTo(showcase, {
          autoAlpha: 0,
          x: isDesktop ? -28 : 0,
          y: isDesktop ? 0 : 18
        }, {
          autoAlpha: 1,
          x: 0,
          y: 0,
          duration: 0.65
        })
        .fromTo(featureCards, {
          autoAlpha: 0,
          y: 14,
          scale: 0.97
        }, {
          autoAlpha: 1,
          y: 0,
          scale: 1,
          duration: 0.42,
          stagger: 0.06
        }, '-=0.28')
        .fromTo(card, {
          autoAlpha: 0,
          x: isDesktop ? 28 : 0,
          y: isDesktop ? 0 : 20,
          scale: 0.985
        }, {
          autoAlpha: 1,
          x: 0,
          y: 0,
          scale: 1,
          duration: 0.62
        }, '-=0.42')
        .fromTo(logo, {
          autoAlpha: 0,
          y: 10
        }, {
          autoAlpha: 1,
          y: 0,
          duration: 0.35
        }, '-=0.26')
        .fromTo(formItems, {
          autoAlpha: 0,
          y: 12
        }, {
          autoAlpha: 1,
          y: 0,
          duration: 0.36,
          stagger: 0.045
        }, '-=0.18')
        .fromTo(actionItems, {
          autoAlpha: 0,
          y: 10
        }, {
          autoAlpha: 1,
          y: 0,
          duration: 0.32,
          stagger: 0.05
        }, '-=0.12');
    }

    if (gsap.matchMedia) {
      gsap.matchMedia().add({ isDesktop: '(min-width: 900px)' }, runIntro);
    } else {
      runIntro({ conditions: { isDesktop: window.innerWidth >= 900 } });
    }
  }

  function initProfileGsap() {
    var profile = document.querySelector('.profile-page');
    if (!profile) return;

    var hero = profile.querySelector('.profile-hero');
    var avatar = profile.querySelector('.profile-avatar');
    var identityItems = toArray(profile.querySelectorAll('.profile-identity > *'));
    var stats = toArray(profile.querySelectorAll('.profile-stat'));
    var tabs = profile.querySelector('.profile-tabs');
    var activePanelCard = toArray(profile.querySelectorAll('.profile-panel'))
      .filter(isVisible)
      .map(function(panel) { return panel.querySelector('.profile-panel-card'); });
    var sections = toArray(profile.querySelectorAll('.profile-section'));

    window.animateProfileTab = function(panel) {
      if (reduceMotion || !panel) return;
      var panelCard = panel.querySelector('.profile-panel-card');
      var details = toArray(panel.querySelectorAll('.form-group, .verification-card, .form-note'));
      var targets = compact([panelCard]).concat(details);
      if (!targets.length) return;

      gsap.fromTo(targets, {
        autoAlpha: 0,
        y: 12,
        scale: 0.99
      }, {
        autoAlpha: 1,
        y: 0,
        scale: 1,
        duration: 0.32,
        ease: 'power2.out',
        stagger: 0.035,
        overwrite: 'auto',
        clearProps: 'transform,opacity,visibility'
      });
    };

    if (reduceMotion) {
      gsap.set(compact([hero, avatar, tabs]).concat(identityItems, stats, activePanelCard, sections), {
        autoAlpha: 1,
        x: 0,
        y: 0,
        scale: 1,
        clearProps: 'transform,opacity,visibility'
      });
      return;
    }

    var tl = gsap.timeline({ defaults: { ease: 'power3.out' } });
    tl.fromTo(hero, {
        autoAlpha: 0,
        y: 24
      }, {
        autoAlpha: 1,
        y: 0,
        duration: 0.58
      })
      .fromTo(avatar, {
        autoAlpha: 0,
        scale: 0.88,
        rotation: -4
      }, {
        autoAlpha: 1,
        scale: 1,
        rotation: 0,
        duration: 0.42
      }, '-=0.34')
      .fromTo(identityItems.concat(stats), {
        autoAlpha: 0,
        y: 12
      }, {
        autoAlpha: 1,
        y: 0,
        duration: 0.34,
        stagger: 0.045
      }, '-=0.24')
      .fromTo(compact([tabs]).concat(activePanelCard), {
        autoAlpha: 0,
        y: 14
      }, {
        autoAlpha: 1,
        y: 0,
        duration: 0.42,
        stagger: 0.07
      }, '-=0.1')
      .fromTo(sections, {
        autoAlpha: 0,
        y: 20
      }, {
        autoAlpha: 1,
        y: 0,
        duration: 0.48,
        stagger: 0.08
      }, '-=0.08');
  }

  function initGsapFieldFocus() {
    if (reduceMotion) return;

    toArray(document.querySelectorAll([
      '.auth-card .form-control',
      '.profile-panel .form-control',
      '.detail-card .form-control',
      '.filter-group input',
      '.filter-group select',
      '.search-input-wrapper input',
      '.chat-sidebar-input-row input'
    ].join(','))).forEach(function(input) {
      var target = input.closest('.password-field') ||
        input.closest('.search-input-wrapper') ||
        input;

      input.addEventListener('focus', function() {
        gsap.to(target, {
          scale: 1.012,
          duration: 0.18,
          ease: 'power2.out',
          overwrite: 'auto'
        });
      });

      input.addEventListener('blur', function() {
        gsap.to(target, {
          scale: 1,
          duration: 0.18,
          ease: 'power2.out',
          overwrite: 'auto',
          clearProps: 'transform'
        });
      });
    });
  }

  function initSiteGsap() {
    if (document.querySelector('.auth-container, .profile-page, .admin-layout')) return;

    var hero = document.querySelector('.hero-section');
    if (hero) {
      revealTargets(toArray(hero.querySelectorAll([
        '.hero-badge',
        '.hero-title',
        '.hero-description',
        '.hero-actions',
        '.hero-meta',
        '.hero-card'
      ].join(','))), {
        y: 24,
        duration: 0.58,
        stagger: 0.075
      });
    }

    var movieHero = document.querySelector('.movie-hero-content');
    if (movieHero) {
      revealTargets(compact([
        movieHero.querySelector('.movie-poster-large, .movie-poster-placeholder')
      ]), {
        x: -22,
        y: 0,
        scale: 0.985,
        duration: 0.52
      });
      revealTargets(toArray(movieHero.querySelectorAll([
        '.genre-tags',
        '.movie-title-large',
        '.movie-meta-bar',
        '.movie-description-main',
        '.action-buttons-group'
      ].join(','))).concat(toArray(movieHero.querySelectorAll('.action-buttons-group + div > *'))), {
        x: 18,
        y: 0,
        duration: 0.5,
        delay: 0.08,
        stagger: 0.055
      });
    }

    var listLayout = document.querySelector('.list-layout');
    if (listLayout) {
      revealTargets(compact([
        listLayout.querySelector('.list-filters'),
        listLayout.querySelector('.list-header')
      ]), {
        y: 18,
        duration: 0.45,
        stagger: 0.08
      });
    }

    var playerLayout = document.querySelector('.player-layout-row');
    if (playerLayout) {
      revealTargets(toArray(playerLayout.children), {
        y: 18,
        duration: 0.5,
        stagger: 0.08
      });
    }

    var searchPage = document.querySelector('.search-page');
    if (searchPage) {
      revealTargets(compact([
        searchPage.querySelector('.search-header-main'),
        searchPage.querySelector('.search-input-group'),
        searchPage.querySelector('.search-initial-trends')
      ]), {
        y: 18,
        duration: 0.5,
        stagger: 0.07
      });
    }

    revealTargets(toArray(document.querySelectorAll([
      '.error-shell',
      '.section > .section-header',
      '.section > .empty-state',
      '.section > div[style*="text-align:center"]',
      '.search-empty-state'
    ].join(','))), {
      y: 16,
      duration: 0.42,
      stagger: 0.06
    });
  }

  function initAdminGsap() {
    var layout = document.querySelector('.admin-layout');
    if (!layout) return;

    var sidebar = layout.querySelector('.admin-sidebar');
    var sidebarItems = toArray(layout.querySelectorAll('.sidebar-nav a'));
    var mainBlocks = toArray(layout.querySelectorAll([
      '.admin-main > .admin-dashboard-hero',
      '.admin-main > .admin-flash-stack',
      '.admin-main > .admin-ops-grid',
      '.admin-main > .stats-grid',
      '.admin-main > .admin-content-grid',
      '.admin-main > .admin-quick-links',
      '.admin-main > .admin-panel',
      '.admin-main > .admin-table-wrap',
      '.admin-main > form'
    ].join(',')));

    revealTargets(compact([sidebar]), {
      x: -18,
      y: 0,
      duration: 0.48
    });
    revealTargets(sidebarItems, {
      x: -10,
      y: 0,
      duration: 0.3,
      delay: 0.08,
      stagger: 0.035
    });
    revealTargets(mainBlocks, {
      y: 18,
      duration: 0.46,
      delay: 0.04,
      stagger: 0.07
    });
    revealTargets(toArray(layout.querySelectorAll([
      '.admin-job-card',
      '.stat-card',
      '.admin-list-panel',
      '.admin-quick-link',
      '.admin-ranked-item',
      'tbody tr'
    ].join(','))), {
      y: 14,
      duration: 0.36,
      delay: 0.16,
      stagger: 0.035
    });

    if (!reduceMotion) {
      toArray(layout.querySelectorAll('.admin-progress-fill')).forEach(function(fill) {
        gsap.fromTo(fill, {
          scaleX: 0,
          transformOrigin: 'left center'
        }, {
          scaleX: 1,
          duration: 0.55,
          delay: 0.26,
          ease: 'power2.out',
          clearProps: 'transform'
        });
      });
    }
  }

  function initScrollRevealGsap() {
    var targets = toArray(document.querySelectorAll([
      '.detail-card',
      '.similar-movie-link',
      '.search-result-horizontal-card:not(.hidden-movie)',
      '.footer-links-col',
      '.footer-brand-section',
      '.footer-bottom'
    ].join(','))).filter(function(el) {
      return isVisible(el) && el.dataset.gsapReveal !== '1';
    });

    if (!hasTargets(targets)) return;

    if (reduceMotion || !('IntersectionObserver' in window)) {
      revealTargets(targets, {
        y: 16,
        duration: reduceMotion ? 0 : 0.36,
        stagger: 0.035
      });
      return;
    }

    gsap.set(targets, { autoAlpha: 0, y: 18 });

    var observer = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);
        gsap.to(entry.target, {
          autoAlpha: 1,
          y: 0,
          duration: 0.42,
          ease: 'power2.out',
          overwrite: 'auto',
          clearProps: 'transform,opacity,visibility'
        });
      });
    }, { rootMargin: '0px 0px -9% 0px', threshold: 0.12 });

    targets.forEach(function(el) {
      el.dataset.gsapReveal = '1';
      observer.observe(el);
    });
  }

  function initGsapPressFeedback() {
    if (reduceMotion) return;

    var selector = [
      '.btn',
      '.watch-btn',
      '.movie-trailer-btn',
      '.show-more-btn',
      '.row-arrow',
      '.mobile-nav-item',
      '.admin-quick-link',
      '.ai-summary-toggle-btn',
      '.chat-suggestion-pill',
      '.suggestion-pill'
    ].join(',');

    function interactiveTarget(event) {
      if (!event.target || !event.target.closest) return null;
      var el = event.target.closest(selector);
      if (!el || el.disabled || el.getAttribute('aria-disabled') === 'true') return null;
      return el;
    }

    document.addEventListener('pointerdown', function(event) {
      var el = interactiveTarget(event);
      if (!el) return;
      gsap.to(el, {
        scale: 0.975,
        duration: 0.08,
        ease: 'power1.out',
        overwrite: 'auto'
      });
    });

    ['pointerup', 'pointercancel', 'pointerout'].forEach(function(type) {
      document.addEventListener(type, function(event) {
        var el = interactiveTarget(event);
        if (!el) return;
        gsap.to(el, {
          scale: 1,
          duration: 0.16,
          ease: 'power2.out',
          overwrite: 'auto',
          clearProps: 'transform'
        });
      });
    });
  }

  function boot() {
    initAuthGsap();
    initProfileGsap();
    initSiteGsap();
    initAdminGsap();
    initScrollRevealGsap();
    initGsapFieldFocus();
    initGsapPressFeedback();
  }

  ready(boot);
  }

  ready(function() {
    loadGsap(startGsapChoreography);
  });
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

// Password visibility toggle
document.addEventListener('click', function(e) {
  if (!e.target || !e.target.closest) return;

  var button = e.target.closest('.password-toggle');
  if (!button) return;

  var field = button.closest('.password-field');
  var input = field ? field.querySelector('input') : null;
  if (!input) return;

  var start = input.selectionStart;
  var end = input.selectionEnd;
  var isVisible = input.type === 'text';
  var icon = button.querySelector('i');

  input.type = isVisible ? 'password' : 'text';
  button.setAttribute('aria-pressed', isVisible ? 'false' : 'true');
  button.setAttribute('aria-label', isVisible ? 'Show password' : 'Hide password');

  if (icon) {
    icon.className = isVisible ? 'ph-bold ph-eye' : 'ph-bold ph-eye-slash';
  }

  try {
    input.focus({ preventScroll: true });
  } catch (err) {
    input.focus();
  }

  if (typeof start === 'number' && typeof end === 'number') {
    try {
      input.setSelectionRange(start, end);
    } catch (err) {}
  }
});

// ── Star Rating ──────────────────────────────────────────────
function initStarRating(container, movieId, currentRating) {
  var stars = container.querySelectorAll('.star');
  var selected = Number(currentRating || 0);
  function scoreFromEvent(star, idx, event) {
    var rect = star.getBoundingClientRect();
    var isHalf = (event.clientX - rect.left) <= rect.width / 2;
    return idx + (isHalf ? 0.5 : 1);
  }
  function paint(n) {
    stars.forEach(function(s, i) {
      s.classList.toggle('filled', i + 1 <= n);
      s.classList.toggle('half', i < n && i + 1 > n);
    });
  }
  paint(selected);
  stars.forEach(function(star, idx) {
    star.addEventListener('mousemove', function(event) { paint(scoreFromEvent(star, idx, event)); });
    star.addEventListener('mouseleave', function() { paint(selected); });
    star.addEventListener('click', async function(event) {
      selected = scoreFromEvent(star, idx, event);
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
      btn.innerHTML = '<i class="ph-fill ph-heart" style="margin-right:6px"></i> In Watchlist';
      btn.classList.add('in-list');
      showToast('Added to Watchlist ❤️', 'success');
    } else {
      btn.innerHTML = '<i class="ph ph-heart" style="margin-right:6px"></i> Add to Watchlist';
      btn.classList.remove('in-list');
      showToast('Removed from Watchlist', 'info');
    }
  } catch(e) { showToast('Error updating watchlist', 'error'); }
}

// ── Mark as Watched ──────────────────────────────────────────
async function markWatched(movieId, btn) {
  try {
    await postFetch('/api/movies/' + movieId + '/watch');
    btn.innerHTML = '<i class="ph-bold ph-check" style="margin-right:6px"></i> Watched';
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

// -- View Transitions for movie navigation --------------------
document.addEventListener('click', function(e) {
  var card = e.target.closest('.movie-card');
  if (!card) return;
  
  var targetUrl = card.getAttribute('onclick') || '';
  if (targetUrl.includes('window.location')) {
    e.preventDefault();
    e.stopPropagation();
    
    var url = targetUrl.match(/'([^']+)'/)[1];
    
    if (document.startViewTransition) {
      document.startViewTransition(function() {
        window.location.href = url;
      });
    } else {
      window.location.href = url;
    }
  }
}, true);

// -- Language Toggle Function ---------------------------------
function toggleLanguage(btn) {
  var currentLang = btn.getAttribute('data-current-lang') || 'en';
  var targetLang = currentLang === 'vi' ? 'en' : 'vi';
  var url = new URL(window.location.href);
  url.searchParams.set('lang', targetLang);
  window.location.href = url.toString();
}
