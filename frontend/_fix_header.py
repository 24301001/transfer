# -*- coding: utf-8 -*-
from pathlib import Path
import re

path = Path(r'D:/aaaa/semesterII/transfer/frontend/src/layouts/MainLayout.vue')
t = path.read_text(encoding='utf-8')

# 1) ellipsis binding
if ':ellipsis="menuEllipsis"' not in t:
    if ':ellipsis="false"' in t:
        t = t.replace(':ellipsis="false"', ':ellipsis="menuEllipsis"', 1)
        print('ellipsis binding updated')
    else:
        print('WARN: ellipsis attr not found')

# 2) import ref
old_imp = "import { computed, onMounted, onUnmounted } from 'vue'"
new_imp = "import { computed, onMounted, onUnmounted, ref } from 'vue'"
if old_imp in t:
    t = t.replace(old_imp, new_imp, 1)
    print('import updated')
elif 'ref' not in t.split("from 'vue'")[0]:
    print('WARN: import pattern different')

# 3) state + computed
if 'menuEllipsis' not in t or 'const isNarrow' not in t:
    needle = 'const isImmersive = computed(() => Boolean(route.meta.immersive))'
    if needle not in t:
        raise SystemExit('isImmersive not found')
    if 'const isNarrow' not in t:
        t = t.replace(
            needle,
            needle
            + """

const isNarrow = ref(false)
const menuEllipsis = computed(() => isNarrow.value)

function updateNarrow() {
  isNarrow.value = window.matchMedia('(max-width: 768px)').matches
}
""",
            1,
        )
        print('added isNarrow/menuEllipsis')
    elif 'menuEllipsis' not in t:
        t = t.replace(
            'const isNarrow = ref(false)',
            "const isNarrow = ref(false)\nconst menuEllipsis = computed(() => isNarrow.value)",
            1,
        )
        print('added menuEllipsis only')

# 4) lifecycle hooks
if 'updateNarrow()' not in t:
    patterns = [
        (
            "onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))\n"
            "onUnmounted(() => window.removeEventListener('scroll', onScroll))",
            """onMounted(() => {
  updateNarrow()
  window.addEventListener('resize', updateNarrow)
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('resize', updateNarrow)
  window.removeEventListener('scroll', onScroll)
})""",
        ),
    ]
    replaced = False
    for old, new in patterns:
        if old in t:
            t = t.replace(old, new, 1)
            replaced = True
            print('patched lifecycle')
            break
    if not replaced:
        m = re.search(
            r"onMounted\(\(\) => window\.addEventListener\('scroll', onScroll, \{ passive: true \}\)\)\s*"
            r"onUnmounted\(\(\) => window\.removeEventListener\('scroll', onScroll\)\)",
            t,
        )
        if m:
            t = (
                t[: m.start()]
                + """onMounted(() => {
  updateNarrow()
  window.addEventListener('resize', updateNarrow)
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('resize', updateNarrow)
  window.removeEventListener('scroll', onScroll)
})"""
                + t[m.end() :]
            )
            print('patched lifecycle via regex')
        else:
            print('WARN: lifecycle not found')
            i = t.find('onMounted')
            print(repr(t[i : i + 220]))

# 5) base overflow
t2 = t.replace(
    """.main-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: $bg;
""",
    """.main-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  max-width: 100vw;
  overflow-x: hidden;
  background: $bg;
""",
    1,
)
if t2 != t:
    t = t2
    print('base overflow added')

# Remove duplicate standalone overflow block if any
dup = """.main-layout {
  overflow-x: hidden;
  max-width: 100vw;
}

.top-header {
  overflow: hidden;
}

"""
if dup in t:
    t = t.replace(dup, '', 1)
    print('removed dup block')

if 'overflow: hidden;\n  position: sticky;' not in t:
    t = t.replace(
        """.top-header {
  position: sticky;
""",
        """.top-header {
  overflow: hidden;
  position: sticky;
""",
        1,
    )
    print('top-header overflow added')

# Ensure mobile user-info hides extras (from previous patch)
if '.username,\n    .role-tag,\n    .dropdown-arrow' not in t and '.username,' not in t[t.find('@media (max-width: 768px)') :]:
    print('WARN: mobile user-info rules may be missing')
else:
    print('mobile user-info rules present')

path.write_text(t, encoding='utf-8', newline='\n')
text = path.read_text(encoding='utf-8')
assert ':ellipsis="menuEllipsis"' in text
assert 'const menuEllipsis' in text
assert 'updateNarrow()' in text
assert 'overflow-x: hidden' in text
print('OK')
