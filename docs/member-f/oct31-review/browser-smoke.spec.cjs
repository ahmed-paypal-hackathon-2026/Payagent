// Run against frontend2 in DEMO MODE only. These checks do not prove live backend/PayPal behavior.
const {test, expect} = require('@playwright/test');
const base = process.env.PAYAGENT_BROWSER_BASE_URL || 'http://localhost:3100';

test.beforeEach(async ({page}) => {
  await page.goto(base);
  await expect(page.getByRole('heading', {name: 'Good to see you, Demo Buyer', exact: true})).toBeVisible();
  await expect(page.getByText('Synthetic data only.', {exact: false})).toBeVisible();
});
async function open(page,id) {
  await page.locator(`a[href="/deals/${id}"]`).click();
  await expect(page.getByRole('heading', {name: 'Landing page redesign for Payagent demo', exact:true}).first()).toBeVisible();
}
test('demo is labelled and has four synthetic scenario links', async ({page}) => {
  for(const id of ['deal-demo-001','deal-demo-002','deal-demo-003','deal-synth-001'])
    await expect(page.locator(`a[href="/deals/${id}"]`)).toBeVisible();
});
test('PR32 clears an unsaved agreement form on identity switch', async ({page}) => {
  await open(page,'deal-demo-001');
  await page.getByRole('button',{name:'Propose a new version',exact:true}).click();
  await page.getByLabel('Price',{exact:true}).fill('777.77');
  await page.getByRole('combobox',{name:'Switch demo user'}).selectOption('user-demo-seller');
  await expect(page.getByLabel('Price',{exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Accept agreement v1',exact:true})).toBeDisabled();
});
test('seller approval completes seeded synthetic dual consent', async ({page}) => {
  await open(page,'deal-demo-001');
  await page.getByRole('combobox',{name:'Switch demo user'}).selectOption('user-demo-seller');
  await page.getByRole('checkbox',{name:'I reviewed the terms and hash, and I accept this version.'}).check();
  await page.getByRole('button',{name:'Accept agreement v1',exact:true}).click();
  await expect(page.getByText('The agreement has dual consent',{exact:true})).toBeVisible();
});
test('one-cent revision advances version and requires fresh consent', async ({page}) => {
  await open(page,'deal-demo-001');
  await page.getByRole('button',{name:'Propose a new version',exact:true}).click();
  await page.getByLabel('Price',{exact:true}).fill('980.01');
  await page.getByRole('button',{name:'Propose agreement',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Agreement v2',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Accept agreement v2',exact:true})).toBeDisabled();
  await expect(page.getByText('$980.01',{exact:true})).toBeVisible();
});
test('mock dispute removes capture control and retains authorization', async ({page}) => {
  await open(page,'deal-demo-003');
  await page.getByLabel('Reason',{exact:true}).fill('Disposable QA: requested revisions are missing.');
  await page.getByRole('button',{name:'Open dispute & freeze capture',exact:true}).click();
  await expect(page.getByText('Dispute freeze is active',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Accept deliverables & release capture',exact:true})).toHaveCount(0);
  await page.getByRole('tab',{name:'Settlement',exact:true}).click();
  await expect(page.getByText('Capture is frozen',{exact:true})).toBeVisible();
  await expect(page.getByText('AUTH-SYNTH-0001',{exact:true})).toBeVisible();
});
