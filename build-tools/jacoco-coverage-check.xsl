<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" encoding="UTF-8"/>
    <xsl:param name="instruction.minimum"/>
    <xsl:param name="branch.minimum"/>

    <xsl:template match="/">
        <xsl:variable name="instructionCovered"
                      select="number(report/counter[@type='INSTRUCTION']/@covered)"/>
        <xsl:variable name="instructionMissed"
                      select="number(report/counter[@type='INSTRUCTION']/@missed)"/>
        <xsl:variable name="branchCovered"
                      select="number(report/counter[@type='BRANCH']/@covered)"/>
        <xsl:variable name="branchMissed"
                      select="number(report/counter[@type='BRANCH']/@missed)"/>
        <xsl:variable name="instructionRatio"
                      select="$instructionCovered div ($instructionCovered + $instructionMissed)"/>
        <xsl:variable name="branchRatio"
                      select="$branchCovered div ($branchCovered + $branchMissed)"/>

        <xsl:text>jacoco.coverage.summary=Aggregate coverage: instruction </xsl:text>
        <xsl:value-of select="format-number($instructionRatio * 100, '0.00')"/>
        <xsl:text>%, branch </xsl:text>
        <xsl:value-of select="format-number($branchRatio * 100, '0.00')"/>
        <xsl:text>%&#10;</xsl:text>
        <xsl:if test="$instructionRatio &gt;= number($instruction.minimum)
                      and $branchRatio &gt;= number($branch.minimum)">
            <xsl:text>jacoco.coverage.passed=true&#10;</xsl:text>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>
