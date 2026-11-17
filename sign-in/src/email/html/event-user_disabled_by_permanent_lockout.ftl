<#import "template.ftl" as layout>
<#import "../sentences.ftl" as say>
<@layout.message title=subject text=msg("kdsPermanentLockoutText", say.moment()) when=say.moment() note=msg("kdsPermanentLockoutNote") />
