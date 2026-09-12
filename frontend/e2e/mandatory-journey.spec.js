import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'

const password = 'StrongPassword123!'
const evidenceDir = process.env.PHOTOSHARE_EVIDENCE_DIR

async function capture(page, name) {
  if (!evidenceDir) return
  await mkdir(evidenceDir, { recursive: true })
  await page.waitForTimeout(300)
  await page.screenshot({ path: join(evidenceDir, name), fullPage: true })
}

async function validPng(page) {
  const base64 = await page.evaluate(async () => {
    const canvas = document.createElement('canvas')
    canvas.width = 16
    canvas.height = 12
    const context = canvas.getContext('2d')
    context.fillStyle = '#0b4f36'
    context.fillRect(0, 0, 16, 12)
    context.fillStyle = '#e8cfa8'
    context.fillRect(3, 2, 10, 8)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
    const bytes = new Uint8Array(await blob.arrayBuffer())
    let binary = ''
    bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
    return btoa(binary)
  })
  return Buffer.from(base64, 'base64')
}

async function signIn(page, email) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test('complete mandatory Admin, Team Member, and customer journey', async ({ page }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10000)}`
  const adminEmail = `admin-${suffix}@example.test`
  const memberEmail = `member-${suffix}@example.test`
  const eventName = `Meera & Arjun ${suffix}`
  const galleryTitle = `Meera & Arjun Gallery ${suffix}`
  const consoleErrors = []
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()) })

  await page.goto('/register')
  await page.getByLabel('Display name').fill('Studio Admin')
  await page.getByLabel('Email').fill(adminEmail)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create Admin account' }).click()
  await expect(page).toHaveURL(/\/admin\/events$/)
  await page.reload()
  await expect(page).toHaveURL(/\/admin\/events$/)
  await expect(page.getByRole('heading', { name: 'Events' })).toBeVisible()

  await page.getByRole('button', { name: 'Create event' }).first().click()
  await page.getByLabel('Event name').fill(eventName)
  await page.getByLabel(/Description/).fill('Integration verification event')
  await page.getByRole('dialog').getByRole('button', { name: 'Create event' }).click()
  await page.getByRole('link', { name: new RegExp(eventName) }).click()
  await expect(page.getByRole('heading', { name: eventName })).toBeVisible()

  await page.getByRole('button', { name: 'Add' }).click()
  await page.getByLabel('Display name').fill('Assigned Photographer')
  await page.getByLabel('Email').fill(memberEmail)
  await page.getByLabel('Initial password').fill(password)
  await page.getByRole('dialog').getByRole('button', { name: 'Add to event' }).click()
  await expect(page.getByText('Assigned Photographer', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Logout' }).click()
  await signIn(page, memberEmail)
  await expect(page).toHaveURL(/\/team\/events$/)
  await page.getByRole('link', { name: new RegExp(eventName) }).click()
  await expect(page.getByRole('heading', { name: 'Your uploads' })).toBeVisible()

  const png = await validPng(page)
  await page.locator('input[type="file"]').setInputFiles([
    { name: 'ceremony-one.png', mimeType: 'image/png', buffer: png },
    { name: 'ceremony-two.png', mimeType: 'image/png', buffer: png },
  ])
  await expect(page.getByRole('heading', { name: 'Upload queue (2)' })).toBeVisible()
  await page.getByRole('button', { name: 'Upload 2 photos' }).click()
  await expect(page.getByText('2 photos uploaded successfully.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Your photos (2)' })).toBeVisible()
  await page.setViewportSize({ width: 1536, height: 1024 })
  await capture(page, 'team-workspace-implemented.png')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(300)
  const mobileMetrics = await page.evaluate(() => ({
    viewportWidth: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    horizontalScroll: window.scrollX,
    sidebar: document.querySelector('.sidebar')?.getBoundingClientRect().toJSON(),
    sidebarClass: document.querySelector('.sidebar')?.className,
    sidebarTransform: getComputedStyle(document.querySelector('.sidebar')).transform,
    main: document.querySelector('.app-main')?.getBoundingClientRect().toJSON(),
  }))
  console.log(`MOBILE_METRICS ${JSON.stringify(mobileMetrics)}`)
  expect(mobileMetrics.documentWidth).toBeLessThanOrEqual(mobileMetrics.viewportWidth)
  expect(mobileMetrics.sidebar.right).toBeLessThanOrEqual(0)
  await page.getByRole('button', { name: 'Toggle navigation' }).click()
  await expect(page.locator('.sidebar-open')).toBeVisible()
  await page.getByRole('button', { name: 'Close navigation' }).click()
  await capture(page, 'team-workspace-mobile-implemented.png')
  await page.setViewportSize({ width: 1536, height: 1024 })

  await page.getByRole('button', { name: 'Logout' }).click()
  await signIn(page, adminEmail)
  await page.getByRole('link', { name: new RegExp(eventName) }).click()
  await expect(page.getByText('2 photos')).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept(galleryTitle))
  await page.getByRole('button', { name: 'Create gallery' }).click()
  await expect(page.locator('input[readonly]').first()).toHaveValue(galleryTitle)
  await page.getByRole('button', { name: 'Select ceremony-one.png' }).click()
  await page.getByRole('button', { name: 'Select ceremony-two.png' }).click()
  await page.getByRole('button', { name: 'Save selection' }).click()
  await expect(page.getByText('Gallery selection saved.')).toBeVisible()
  await page.getByLabel('Gallery PIN').fill('123456')
  await page.getByRole('button', { name: 'Save PIN' }).click()
  await expect(page.getByText(/Gallery PIN saved/)).toBeVisible()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Publish gallery' }).click()
  await expect(page.getByText('Gallery published successfully.')).toBeVisible()
  await page.setViewportSize({ width: 1487, height: 1058 })
  await capture(page, 'admin-workspace-implemented.png')
  await page.setViewportSize({ width: 800, height: 1000 })
  await capture(page, 'admin-workspace-tablet-implemented.png')
  await page.setViewportSize({ width: 1487, height: 1058 })
  const shareUrl = await page.getByRole('textbox', { name: 'Gallery link' }).inputValue()
  expect(shareUrl).toContain('/gallery/')

  await page.getByRole('button', { name: 'Logout' }).click()
  await page.goto(shareUrl)
  await expect(page.getByRole('heading', { name: 'Enter gallery PIN' })).toBeVisible()
  expect(await page.getByText(/sign in|register/i).count()).toBe(0)
  await page.setViewportSize({ width: 390, height: 844 })
  await capture(page, 'public-pin-mobile-implemented.png')
  await page.setViewportSize({ width: 1536, height: 1024 })
  await page.getByLabel('Gallery PIN').fill('123456')
  await page.getByRole('button', { name: 'Open gallery' }).click()
  await expect(page.getByRole('heading', { name: galleryTitle })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Open photo 1 of 2' })).toBeVisible()
  await capture(page, 'public-gallery-implemented.png')
  await page.setViewportSize({ width: 390, height: 844 })
  await capture(page, 'public-gallery-mobile-implemented.png')
  await page.getByRole('button', { name: 'Open photo 1 of 2' }).click()
  await expect(page.getByRole('dialog', { name: 'Photo 1 of 2' })).toBeVisible()
  await expect(page.locator('.lightbox-image')).toBeVisible()
  await capture(page, 'public-lightbox-mobile-implemented.png')
  await page.getByRole('button', { name: 'Close preview' }).click()
  expect(consoleErrors).toEqual([])
})
