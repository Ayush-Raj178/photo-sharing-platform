import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'

const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
const pageResponse = (page, items) => ({ items, page, pageSize: 24, totalItems: 2, totalPages: 2, hasNext: page === 0 })
const photo = (id, filename) => ({ id, eventId: '1', uploadedBy: '7', filename, contentType: 'image/png', fileSizeBytes: 68, widthPx: 1, heightPx: 1, createdAt: '2026-09-10T00:00:00Z', contentPath: `/api/v1/events/1/photos/${id}/content` })

async function capture(page, filename) {
  const directory = process.env.PHOTOSHARE_BONUS_EVIDENCE_DIR
  if (!directory) return
  await mkdir(directory, { recursive: true })
  await page.screenshot({ path: join(directory, filename), fullPage: true })
}

test('Admin loads more, filters server-side, and saves gallery expiration', async ({ page }) => {
  const requests = []
  let gallery = { id: '9', eventId: '1', title: 'Wedding', status: 'PUBLISHED', photoIds: ['1'], pinSet: true, publishedAt: '2026-09-10T00:00:00Z', expiresAt: null, shareUrl: 'http://localhost:5173/gallery/demo' }
  await page.route('http://localhost:8080/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.pathname.endsWith('/content')) return route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
    if (url.pathname === '/api/v1/auth/me') return route.fulfill({ json: { id: '1', email: 'admin@example.test', displayName: 'Admin', role: 'ADMIN' } })
    if (url.pathname === '/api/v1/events/1') return route.fulfill({ json: { id: '1', name: 'Test Wedding', description: '' } })
    if (url.pathname === '/api/v1/events/1/members') return route.fulfill({ json: { items: [{ user: { id: '7', email: 'alex@example.test', displayName: 'Alex', role: 'TEAM_MEMBER' } }] } })
    if (url.pathname === '/api/v1/team-members') return route.fulfill({ json: { items: [] } })
    if (url.pathname === '/api/v1/events/1/galleries') return route.fulfill({ json: { items: [gallery] } })
    if (url.pathname === '/api/v1/events/1/galleries/9/expiry') {
      gallery = { ...gallery, expiresAt: JSON.parse(request.postData()).expiresAt }
      return route.fulfill({ json: gallery })
    }
    if (url.pathname === '/api/v1/events/1/photos') {
      requests.push(Object.fromEntries(url.searchParams))
      const pageNumber = Number(url.searchParams.get('page') || 0)
      return route.fulfill({ json: pageResponse(pageNumber, [photo(String(pageNumber + 1), pageNumber ? 'second.png' : 'first.png')]) })
    }
    return route.fulfill({ status: 404, json: { error: { message: 'Synthetic route not found' } } })
  })
  await page.addInitScript(() => sessionStorage.setItem('photoshare.staffSession', JSON.stringify({
    accessToken: 'synthetic-staff-token', expiresAt: Date.now() + 60_000,
    user: { id: '1', displayName: 'Admin', role: 'ADMIN' },
  })))
  const browserErrors = []
  page.on('console', (message) => { if (message.type() === 'error') browserErrors.push(message.text()) })
  await page.goto('/admin/events/1')
  await expect(page.getByRole('heading', { name: 'Test Wedding' })).toBeVisible()
  await page.getByRole('button', { name: 'Load more photos' }).click()
  await expect(page.getByText('second.png')).toBeVisible()
  await page.getByLabel('Search filename').fill('portrait')
  await page.getByLabel('Uploader').selectOption('7')
  await page.getByLabel('Gallery selection').selectOption('selected')
  await expect.poll(() => requests.some((parameters) => parameters.search === 'portrait' && parameters.uploaderId === '7' && parameters.selected === 'true')).toBe(true)
  await page.getByLabel('Expiration (optional)').fill('2030-01-01T10:30')
  await page.getByRole('button', { name: 'Save expiration' }).click()
  await expect(page.getByText('Gallery expiration saved.')).toBeVisible()
  expect(browserErrors).toEqual([])
  await capture(page, 'admin-pagination-filter-expiry.png')
})

test('customer loads a later protected gallery page', async ({ page }) => {
  let galleryPage = 0
  await page.route('http://localhost:8080/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.pathname.endsWith('/content')) return route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
    if (url.pathname.endsWith('/access')) return route.fulfill({ json: { accessToken: 'synthetic-gallery-grant', tokenType: 'Bearer', expiresIn: 900 } })
    galleryPage = Number(url.searchParams.get('page') || 0)
    return route.fulfill({ json: { title: 'Customer Gallery', photos: [{ id: String(galleryPage + 1), contentPath: `/api/v1/public/galleries/demo/photos/${galleryPage + 1}/content`, widthPx: 1, heightPx: 1 }], page: galleryPage, pageSize: 24, totalItems: 2, totalPages: 2, hasNext: galleryPage === 0 } })
  })
  await page.goto('/gallery/demo')
  await page.getByLabel('Gallery PIN').fill('123456')
  await page.getByRole('button', { name: 'Open gallery' }).click()
  await page.getByRole('button', { name: 'Load more photos' }).click()
  await expect(page.getByRole('button', { name: 'Open photo 2 of 2' })).toBeVisible()
  expect(galleryPage).toBe(1)
  await capture(page, 'public-gallery-load-more.png')
})
