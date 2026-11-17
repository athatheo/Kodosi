<#import "template.ftl" as layout>
<@layout.message title=subject text=msg("kdsNewEmailText", realmName, newEmail) action=msg("kdsNewEmailAction") link=link note=msg("kdsNewEmailNote", linkExpirationFormatter(linkExpiration)) />
