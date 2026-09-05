import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/index.css'
import { initI18n } from './i18n'
import { guardContentCopy } from './utils/guardContentCopy'
import App from './App'

guardContentCopy()

// Ждём готовности i18n (активная локаль загружена) — иначе на старте мелькнут сырые ключи.
void initI18n().finally(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
