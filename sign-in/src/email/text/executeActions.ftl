<#ftl output_format="plainText">
<#import "../sentences.ftl" as say>
${subject}

${say.setupText()}

${link}

${msg("kdsSetupNote", linkExpirationFormatter(linkExpiration))}
