<#
.SYNOPSIS
  Seeds staging demo organizations/users/stock/driver via real REST APIs (no DB seeder).

.DESCRIPTION
  Prerequisites:
  - Empty DB migrated through V94
  - Super admin already bootstrapped and bootstrap flag disabled
  - Backend reachable at -BaseUrl

  Does not print or log passwords beyond the process that invoked it.
  Adjust payloads if your staging API validation differs.

.EXAMPLE
  .\staging-seed-via-api.ps1 -BaseUrl https://staging.example.com -AdminEmail admin@example.com -AdminPassword '***'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$AdminEmail,
    [Parameter(Mandatory = $true)][string]$AdminPassword,
    [string]$CustomerEmail = "customer.staging@example.com",
    [string]$CustomerPassword = "ChangeMe1!",
    [string]$ManufacturerEmail = "mfr.admin.staging@example.com",
    [string]$DistributorEmail = "dist.admin.staging@example.com",
    [string]$DriverPhone = "0711000001"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null,
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $hdrs = @{ "Content-Type" = "application/json"; "Accept" = "application/json" }
    if ($Token) { $hdrs["Authorization"] = "Bearer $Token" }
    foreach ($k in $Headers.Keys) { $hdrs[$k] = $Headers[$k] }
    $json = if ($null -ne $Body) { $Body | ConvertTo-Json -Depth 8 } else { $null }
    Write-Host "$Method $Path"
    if ($null -ne $json) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $hdrs -Body $json
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $hdrs
}

function Login([string]$Email, [string]$Password) {
    $resp = Invoke-Json -Method POST -Path "/api/auth/login" -Body @{
        email = $Email
        password = $Password
    }
    if (-not $resp.accessToken) { throw "Login failed for $Email" }
    return $resp.accessToken
}

Write-Host "=== Login super admin ==="
$adminToken = Login $AdminEmail $AdminPassword

function Submit-And-Approve-Org {
    param(
        [string]$OrgType, # MANUFACTURER | DISTRIBUTOR
        [string]$OrgName,
        [string]$PersonalEmail,
        [string]$OrgEmail,
        [string]$RegSuffix
    )
    $create = Invoke-Json -Method POST -Path "/api/access-requests" -Body @{
        organizationType = $OrgType
        organizationName = $OrgName
        country = "Kenya"
        registrationNumber = "REG-$RegSuffix"
        taxIdentifier = "TAX-$RegSuffix"
        fullName = "$OrgName Admin"
        representativeRole = "Manager"
        personalEmail = $PersonalEmail
        organizationEmail = $OrgEmail
        phoneNumber = "0712$RegSuffix"
        authorizedRepresentative = $true
    }
    $id = $create.id
    if (-not $id) { throw "Access request create returned no id for $OrgName" }
    Write-Host "Created access request $id for $OrgName — approving"
    $approved = Invoke-Json -Method POST -Path "/api/admin/access-requests/$id/approve" -Token $adminToken
    return $approved
}

Write-Host "=== Manufacturer + distributor access requests ==="
$mfr = Submit-And-Approve-Org -OrgType "MANUFACTURER" -OrgName "Staging Manufacturer" `
    -PersonalEmail $ManufacturerEmail -OrgEmail "org.$ManufacturerEmail" -RegSuffix "1001"
$dist = Submit-And-Approve-Org -OrgType "DISTRIBUTOR" -OrgName "Staging Distributor" `
    -PersonalEmail $DistributorEmail -OrgEmail "org.$DistributorEmail" -RegSuffix "1002"

Write-Host "Activation emails may be required before org admins can login."
Write-Host "If activation tokens are only in email, complete activation in LogiFlow, then re-run stock steps with -ManufacturerToken."

Write-Host "=== Register customer (CUSTOMER role) ==="
try {
    Invoke-Json -Method POST -Path "/api/auth/register" -Body @{
        fullName = "Staging Customer"
        email = $CustomerEmail
        phoneNumber = "0711222333"
        password = $CustomerPassword
        role = "CUSTOMER"
    } | Out-Null
    Write-Host "Customer registered (verify email if required): $CustomerEmail"
} catch {
    Write-Host "Customer register skipped/failed (may already exist): $($_.Exception.Message)"
}

Write-Host @"

Manual follow-ups after activation emails (still via API / LogiFlow UI — no SQL):
1. Manufacturer admin: create warehouse POST /api/v1/warehouses (or LogiFlow warehouse screen).
2. Create published stock item POST /api/v1/stock/items and intake quantity.
3. Approve distributor product authorization.
4. Distributor admin: register driver POST /api/v1/drivers (or org driver invite), then admin-approve if required.
5. Customer: place order + Daraja STK against sandbox; confirm receipt is not DEMO.

Script stopped before stock/driver mutations that need activated manufacturer/distributor tokens.
Pass tokens in a follow-up invocation once activation is done.
"@
