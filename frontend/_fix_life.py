# -*- coding: utf-8 -*-
from pathlib import Path

path = Path(r'D:/aaaa/semesterII/transfer/frontend/src/layouts/MainLayout.vue')
t = path.read_text(encoding='utf-8')

old = (
    "onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))\n"
    "onUnmounted(() => window.removeEventListener('scroll', onScroll))"
)
new = """onMounted(() => {
  updateNarrow()
  window.addEventListener('resize', updateNarrow)
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('resize', updateNarrow)
  window.removeEventListener('scroll', onScroll)
})"""

if old not in t:
    i = t.find('onMounted')
    print('NOT FOUND, actual:', repr(t[i:i + 200]))
    raise SystemExit(1)

path.write_text(t.replace(old, new, 1), encoding='utf-8', newline='\n')
print('OK')
