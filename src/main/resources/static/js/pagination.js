function initPagination(containerId, baseUrl) {
  var container = document.getElementById(containerId);
  if (!container) return;

  var current = parseInt(container.getAttribute('data-current'));
  var total   = parseInt(container.getAttribute('data-total'));

  function buildUrl(p) {
    return baseUrl + '?page=' + p; // ✅ dynamic URL
  }

  function btn(page, label, cls) {
    var a = document.createElement('a');
    a.href = buildUrl(page);
    a.className = 'page-btn' + (cls ? ' ' + cls : '');
    a.textContent = label;
    return a;
  }

  function dots() {
    var s = document.createElement('span');
    s.className = 'page-btn dots';
    s.textContent = '…';
    return s;
  }

  var frag = document.createDocumentFragment();

  if (current > 0) {
    frag.appendChild(btn(current - 1, '‹ Prev', 'nav'));
  }

  var range = new Set();
  range.add(0);
  range.add(total - 1);

  for (var i = Math.max(0, current - 2); i <= Math.min(total - 1, current + 2); i++) {
    range.add(i);
  }

  var pages = Array.from(range).sort((a,b)=>a-b);

  var prev = -1;
  pages.forEach(function(p) {
    if (prev >= 0 && p - prev > 1) frag.appendChild(dots());
    frag.appendChild(btn(p, p + 1, p === current ? 'active' : ''));
    prev = p;
  });

  if (current < total - 1) {
    frag.appendChild(btn(current + 1, 'Next ›', 'nav'));
  }

  // Jump UI
  var jump = document.createElement('div');
  jump.style.cssText = 'display:flex;align-items:center;gap:.4rem;margin-left:.5rem';

  jump.innerHTML =
    '<span style="color:#555;font-size:.82rem">Go to</span>' +
    '<input id="' + containerId + '-jump" type="number" min="1" max="' + total + '" value="' + (current+1) + '" style="width:56px;height:38px;">' +
    '<button onclick="jumpTo(\'' + containerId + '\', \'' + baseUrl + '\')">Go</button>';

  frag.appendChild(jump);

  container.innerHTML = '';
  container.appendChild(frag);

  container.style.cssText =
    'display:flex;align-items:center;justify-content:center;gap:.35rem;margin-top:2.5rem;flex-wrap:wrap';
}

// ✅ FIX jumpTo để reusable
function jumpTo(containerId, baseUrl) {
  var container = document.getElementById(containerId);
  var input = document.getElementById(containerId + '-jump');

  var val = parseInt(input.value);
  var total = parseInt(container.getAttribute('data-total'));

  if (val >= 1 && val <= total) {
    window.location.href = baseUrl + '?page=' + (val - 1);
  }
}