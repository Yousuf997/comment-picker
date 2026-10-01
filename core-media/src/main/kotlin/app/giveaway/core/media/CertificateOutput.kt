package app.giveaway.core.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.OutputStream
import javax.inject.Inject

/** Writes the certificate files. Replaced in JVM tests, where PdfDocument has no native backing. */
interface CertificateOutput {
    fun writePdf(content: CertificateContent, style: CertificateStyle, out: OutputStream)

    fun writePng(content: CertificateContent, style: CertificateStyle, out: OutputStream)
}

internal class RendererCertificateOutput @Inject constructor() : CertificateOutput {
    override fun writePdf(content: CertificateContent, style: CertificateStyle, out: OutputStream) =
        CertificateRenderer(style).writePdf(content, out)

    override fun writePng(content: CertificateContent, style: CertificateStyle, out: OutputStream) =
        CertificateRenderer(style).writePng(content, out)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CertificateModule {
    @Binds
    internal abstract fun certificateOutput(impl: RendererCertificateOutput): CertificateOutput
}
