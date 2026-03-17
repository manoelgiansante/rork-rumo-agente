# Google Play Console Submission

This skill provides expert knowledge on submitting Android apps to the Google Play Store via Play Console.

## When to Use

- Preparing an app for Google Play submission
- Understanding testing tracks (internal, closed, open, production)
- First-time submission requirements (manual AAB upload)
- Configuring in-app products and subscriptions in Play Console
- Navigating Play Console policies and content requirements
- Understanding review timelines and requirements

## Testing Tracks Overview

Google Play provides multiple testing tracks before production release:

| Track | Testers | Review Time | Use Case |
|-------|---------|-------------|----------|
| Internal | Up to 100 | Instant | Quick smoke testing, core team |
| Closed | Limited list | 3-5 days | Beta testers, stakeholder review |
| Open | Anyone can join | 3-5 days | Public beta, wide feedback |
| Production | All users | 1-7 days | Full public release |

### Internal Testing (Recommended Start)

- **Maximum testers:** 100 (you choose them)
- **Availability:** Instant (no review)
- **Best for:** Quick iteration, smoke testing
- **Note:** Can start before completing all store listing requirements

### Closed Testing

- **Testers:** You create and manage tester lists
- **Availability:** After Google review (3-5 days)
- **Best for:** Controlled beta testing
- **Note:** Required for personal accounts before production (12+ testers, 14+ days)

### Open Testing

- **Testers:** Anyone can join via Play Store
- **Availability:** After Google review
- **Best for:** Wide feedback collection
- **Note:** Shows as "Early access" in Play Store

### Production

- **Users:** All Play Store users
- **Availability:** After Google review
- **Best for:** Public release
- **Note:** First review typically longer (7+ days)

## First-Time Submission Requirements

### For ALL New Apps

1. **Manual AAB Upload Required**
   - First build must be manually uploaded
   - Download AAB from EAS: `eas build --platform android --profile production`
   - Upload to Play Console → Release → Production → Create release
   - After first release, EAS `--auto-submit` works

2. **Complete Store Listing**
   - App name, descriptions
   - App icon (512x512)
   - Feature graphic (1024x500)
   - At least 2 screenshots

3. **Complete App Content**
   - Content rating questionnaire
   - Target audience declaration
   - Data safety form
   - Privacy policy URL

### For Personal Developer Accounts (Post-Nov 2023)

Google requires additional verification for new personal accounts:

1. **Testing Requirement**
   - Must run closed testing with **12+ opted-in testers**
   - Testers must be opted in for **14+ continuous days**
   - Complete this BEFORE applying for production access

2. **Device Verification**
   - Must verify access to a physical Android device
   - Done via Play Console mobile app

3. **Timeline Impact**
   - Plan for 14+ days of closed testing
   - Then 7+ days for production review
   - Total: ~3 weeks minimum for first release

**Organisation accounts are EXEMPT from the 14-day testing requirement.**

## Store Listing Requirements

### Text Content

| Field | Max Length | Required |
|-------|------------|----------|
| App name | 30 chars | Yes |
| Short description | 80 chars | Yes |
| Full description | 4000 chars | Yes |

**Content Rules:**
- No competitor names or trademarks
- No pricing information (use IAP sections)
- No excessive keywords (keyword stuffing)
- Must match actual app functionality

### Graphics

| Asset | Size | Format | Required |
|-------|------|--------|----------|
| App icon | 512x512 | PNG | Yes |
| Feature graphic | 1024x500 | PNG/JPG | Yes |
| Phone screenshots | Varies | PNG/JPG | Min 2 |
| Tablet screenshots | Varies | PNG/JPG | If tablet support |

**Screenshot Guidelines:**
- Show actual app functionality
- No device frames with non-Android devices
- No misleading content
- Minimum 2, maximum 8 per device type

## App Content Requirements

### Content Rating

Complete the IARC questionnaire:
- Answer honestly about content
- Categories: Violence, Sexual content, Language, etc.
- Automatic rating assignment based on answers
- **Unrated apps will be rejected**

### Target Audience

Declare your target age group:
- Under 13 (additional requirements apply)
- 13-17
- 18+
- All ages

**If targeting children:**
- Must comply with Families Policy
- Additional content restrictions
- Teacher Approved program (optional)

### Data Safety

Declare your app's data practices:
- What data is collected
- How data is used
- Is data shared with third parties
- Security practices (encryption, deletion)

**Must be accurate** - Google may verify against actual app behaviour.

### Privacy Policy

Required for ALL apps that:
- Collect personal data
- Handle sensitive permissions
- Target children

**Requirements:**
- Accessible URL (HTTPS)
- Also accessible from within the app
- Must describe data practices accurately

## In-App Products

### Product Types

| Type | Description | Use Case |
|------|-------------|----------|
| One-time | Single purchase, permanent | Premium features, remove ads |
| Subscription | Recurring payment | Premium access, content |
| Consumable | Can be purchased multiple times | Credits, virtual currency |

### Subscription Configuration

**Base Plans:**
- Define pricing period (monthly, yearly, etc.)
- Set prices per country
- Configure grace period

**Offers:**
- Free trials
- Introductory prices
- Upgrade/downgrade offers

**Grace Period:**
- Days after failed payment before subscription ends
- Recommended: 7-30 days

### Product Status

| Status | Meaning |
|--------|---------|
| Active | Available for purchase |
| Inactive | Not available (draft or disabled) |
| Pending | Awaiting review |

## Release Management

### Creating a Release

1. Navigate to Release → [Track] → Create new release
2. Upload AAB file (or use Play App Signing)
3. Add release notes
4. Review and roll out

### Release Notes

Required for closed/open testing and production:
- Describe what's new in this version
- Keep concise and user-friendly
- Localise for target markets

### Staged Rollouts (Production)

For production releases, you can:
- Start with a percentage of users
- Monitor for issues
- Gradually increase or halt

**Recommended for major updates:**
- Start at 5-10%
- Monitor crash rates and reviews
- Increase over 3-7 days

## Common Rejection Reasons

### Policy Violations

1. **Deceptive Behaviour**
   - App doesn't match description
   - Hidden functionality
   - Misleading permissions

2. **Privacy Issues**
   - Missing/inaccessible privacy policy
   - Undeclared data collection
   - Data safety form inaccurate

3. **Content Issues**
   - Age rating mismatch
   - Inappropriate content for declared audience
   - Copyrighted/trademarked content

### Technical Issues

1. **API Level**
   - Must meet minimum target API requirements
   - Currently: targetSdkVersion 34+ required

2. **64-bit Requirement**
   - Must include 64-bit native libraries
   - arm64-v8a required

3. **Signing Issues**
   - Must use same signing key for updates
   - Consider Play App Signing for key management

## Review Timelines

| Scenario | Typical Timeline |
|----------|------------------|
| Internal testing | Instant |
| Closed/Open testing (first) | 3-5 days |
| Closed/Open testing (updates) | 1-3 days |
| Production (first app) | 7+ days |
| Production (updates) | 1-3 days |
| Policy appeal | 7-14 days |

**Note:** Holiday periods may extend review times.

## Play Console Navigation

### Key Sections

- **Dashboard:** App overview and status
- **Grow → Store presence:** Store listing, settings
- **Policy and programs → App content:** Content rating, data safety, etc.
- **Monetize → Products:** In-app products, subscriptions
- **Release → Testing/Production:** Release management
- **Quality → Android vitals:** Crash rates, ANRs

### Direct URLs

```
Base: https://play.google.com/console/u/0/developers/{dev_id}/app/{app_id}

Store listing:     .../store-listing
Content rating:    .../content-rating
Data safety:       .../data-safety
In-app products:   .../managed-products
Subscriptions:     .../subscriptions
Internal testing:  .../tracks/internal-testing
Closed testing:    .../tracks/closed-testing
Open testing:      .../tracks/open-testing
Production:        .../production
```

## Best Practices

1. **Start with internal testing** - No review delay, instant distribution
2. **Complete store listing early** - Can take time to prepare assets
3. **Be accurate in data safety** - Google verifies claims
4. **Plan for 14-day testing** - If personal account, factor this in
5. **Use staged rollouts** - Catch issues before full release
6. **Monitor Android vitals** - Crash rates affect visibility
7. **Respond to reviews** - Especially negative ones
8. **Keep keys secure** - Use Play App Signing if possible

## EAS Integration

### First Release Workflow

```bash
# 1. Build production AAB
eas build --platform android --profile production

# 2. Download from EAS dashboard
# 3. Manually upload to Play Console
# 4. Complete store listing and content
# 5. Submit for review
```

### Subsequent Releases

```bash
# Auto-submit to internal track
eas build --platform android --profile production --auto-submit

# Or specify track in eas.json
# submit.production.android.track: "internal"
```

### Service Account for Auto-Submit

1. Create Google Cloud service account
2. Grant Play Console API access
3. Assign "Release manager" permissions
4. Download JSON key
5. Reference in `eas.json`:

```json
{
  "submit": {
    "production": {
      "android": {
        "serviceAccountKeyPath": "./play-store-credentials.json",
        "track": "internal"
      }
    }
  }
}
```
