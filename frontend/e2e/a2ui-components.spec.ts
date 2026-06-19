import { expect, test } from '@playwright/test'

const EVIDENCE_DIR = 'test-evidence'

test.describe('A2UI component rendering', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/?test-mode=true')
    // Wait for the page to load by checking for the input field
    await page.waitForSelector('input', { timeout: 10000 })
  })

  test('renders line chart for trend query', async ({ page }) => {
    const input = page.locator('input')
    await input.fill('Show me monthly trends')
    await input.press('Enter')

    const chart = page.getByTestId('a2ui-line_chart')
    await expect(chart).toBeVisible({ timeout: 5000 })
    await chart.screenshot({ path: `${EVIDENCE_DIR}/line-chart.png` })
  })

  test('renders metric card for summary query', async ({ page }) => {
    const input = page.locator('input')
    await input.fill('Show me summary')
    await input.press('Enter')

    const card = page.getByTestId('a2ui-metric_card')
    await expect(card).toBeVisible({ timeout: 5000 })
    await page.screenshot({ path: `${EVIDENCE_DIR}/metric-card.png`, fullPage: true })
  })

  test('renders data table for list query', async ({ page }) => {
    const input = page.locator('input')
    await input.fill('List all clients')
    await input.press('Enter')

    const table = page.getByTestId('a2ui-data_table')
    await expect(table).toBeVisible({ timeout: 5000 })
    await table.screenshot({ path: `${EVIDENCE_DIR}/data-table.png` })
  })

  test('renders composite dashboard for overview query', async ({ page }) => {
    const input = page.locator('input')
    await input.fill('dashboard overview')
    await input.press('Enter')

    const dashboard = page.getByTestId('a2ui-dashboard')
    await expect(dashboard).toBeVisible({ timeout: 5000 })
    await expect(page.getByTestId('a2ui-metric_card')).toBeVisible()
    await expect(page.getByTestId('a2ui-data_table')).toBeVisible()
    await page.screenshot({ path: `${EVIDENCE_DIR}/dashboard.png`, fullPage: true })
  })

  test('renders confirmation card for add query', async ({ page }) => {
    const input = page.locator('input')
    await input.fill('Create new client')
    await input.press('Enter')

    const card = page.getByTestId('a2ui-confirmation_card')
    await expect(card).toBeVisible({ timeout: 5000 })
    await expect(page.getByTestId('btn-approve')).toBeVisible()
    await expect(page.getByTestId('btn-cancel')).toBeVisible()
    await card.screenshot({ path: `${EVIDENCE_DIR}/confirmation-card.png` })
  })
})
