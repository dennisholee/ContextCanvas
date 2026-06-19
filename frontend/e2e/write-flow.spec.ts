import { expect, test } from '@playwright/test'

const EVIDENCE_DIR = 'test-evidence'

test.describe('Write flow with confirmation gate', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/?test-mode=true')
    await page.waitForSelector('input', { timeout: 10000 })
  })

  test('user sees confirmation card before write is executed', async ({ page }) => {
    const input = page.locator('input')
    await input.fill('Add a new sale of $5,000 for Acme Corp')
    await input.press('Enter')

    const confirmationCard = page.getByTestId('a2ui-confirmation_card')
    await expect(confirmationCard).toBeVisible({ timeout: 5000 })

    await expect(confirmationCard).toContainText('CREATE')
    await expect(confirmationCard).toContainText('deal_amount')

    await expect(page.getByTestId('btn-approve')).toBeVisible()
    await expect(page.getByTestId('btn-cancel')).toBeVisible()

    await page.screenshot({ path: `${EVIDENCE_DIR}/write-flow-confirm.png`, fullPage: true })
  })
})
