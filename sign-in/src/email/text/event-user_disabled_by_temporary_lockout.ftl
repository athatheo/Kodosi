<#ftl output_format="plainText">
<#import "../sentences.ftl" as say>
${subject}

${msg("kdsTemporaryLockoutText", say.moment())}

${msg("kdsLockoutNote")}
