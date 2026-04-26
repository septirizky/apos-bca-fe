package com.bandjak.pos.apos

import android.util.Base64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class DeepLinkEncryptionUtil {
    fun encrypt(plainString: String): String? {
        var encryptedString = ""

        for (splittedDataString in plainString.chunked(MAX_LENGTH)) {
            encryptedString += "${encryptPartData(splittedDataString)}."
        }

        return encryptedString.dropLast(1)
    }

    fun generateSignature(serialNumber: String): String {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            .apply {
                initSign(loadPrivateKey())
                update(serialNumber.toByteArray())
            }
            .sign()

        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    private fun encryptPartData(
        plainString: String,
        publicKey: PublicKey = loadPublicKey()
    ): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        return Base64.encodeToString(cipher.doFinal(plainString.toByteArray()), Base64.NO_WRAP)
    }

    private fun loadPrivateKey(): PrivateKey {
        val privateKeyBytes = Base64.decode(PARTNER_PRIVATE_KEY, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(wrapPkcs1PrivateKey(privateKeyBytes))
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)

        return keyFactory.generatePrivate(keySpec)
    }

    private fun loadPublicKey(): PublicKey {
        val publicKeyBytes = Base64.decode(APOS_PUBLIC_KEY, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)

        return keyFactory.generatePublic(keySpec)
    }

    private fun wrapPkcs1PrivateKey(pkcs1PrivateKey: ByteArray): ByteArray {
        val privateKeyInfoHeader = byteArrayOf(
            0x30, 0x82.toByte(), ((pkcs1PrivateKey.size + 22) shr 8).toByte(),
            (pkcs1PrivateKey.size + 22).toByte(), 0x02, 0x01, 0x00, 0x30,
            0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00, 0x04,
            0x82.toByte(), (pkcs1PrivateKey.size shr 8).toByte(),
            pkcs1PrivateKey.size.toByte()
        )

        return privateKeyInfoHeader + pkcs1PrivateKey
    }

    companion object {
        private const val RSA_ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val MAX_LENGTH = 200

        private const val APOS_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+R6FeJYb9hWld2iHA73r\n" +
                "w0RW/p1J7TXdfwGNMzSLT3h6lvxelp6P4+g8aO/we+2amCSFu/D8ovTI3mIVx+wD\n" +
                "KmM+qUYti2z9Qr7oiqA/5eKVCpvpLnf0SjyVq/jv1HoB5qOdlVEQrcUdU7MBnYw6\n" +
                "JP/EmuXCLmGjNifDnURv15nQIOTl+luzLUDvC7ANvIUpOlzAVDIAnyiKIL8yiJ8m\n" +
                "vzqsYyHPKvXq2cB0rixKkEcUyp860B38SQ/VHgbIcAlNsTKEgIk3Qa2Ph3kOZEMG\n" +
                "6tpQR/7kENKry/l5Y0PltASf/z1qRITyCweqXwb4KFqCwpi4KYhAjtyI2gCguZj1\n" +
                "zQIDAQAB"

        private const val PARTNER_PRIVATE_KEY =
            "MIIEowIBAAKCAQEAnUszSgCCRGpS+4kP+y7xHeviSVAsaY7T9am9E3WAZBqRtAi6\n" +
                "xMSgiu+YqDC1eQgaEzvwYLXJNmCOWTEgXB0CvhBQ0pZuKTI/tBfjr738PbfiD9RN\n" +
                "5kyyCI8S1Rip38iUFKDRgQI17+9bA1993/sC2J/kbG8ubQj3+OTFft3+4FMw7VhY\n" +
                "DRaBVe42pHU/UqHp/AafoWblwQPjQf8QOO7Yihz+cDaopVEPxQN90OXXdvmlgafL\n" +
                "fOwr6T0idQOW9PR5Gu0aje24/y+Kx6uSDXFfRX+l5JsdQ+zzHWSsv7kuxk74zhE5\n" +
                "54wF+VQaQ8ptMzZhl2G2Ea9Lv0KS1olYUWNyWQIDAQABAoIBAB5Ib5/0OCBG3iIa\n" +
                "Uc7Yy0go9WCLBHnwKyO1Ybcg3K6pJNsmARtIBeap2VisRAwAwNBqLk3YQdxru4w1\n" +
                "dpb1aOVBy+W3W48n4vbf0JCxwaH6SJYmmDbaFj6qmQQY4v+4JLZR/fPaAptmmD2u\n" +
                "gVQBhhtBsV8lCqmoW+F53gIeaGc0n7Xs91R4lAFN43/t9aDuIRQqkuLRhWaqecDL\n" +
                "4L/68O7aWPn0b8P3czdeXte+vwQeBbnaiwHD0LF1vYs/NeavVJYb+UVcjIcgd4KG\n" +
                "+C1Y1W+kAyESEK39sq14EjIrzQjut3N4G7RdifUyC+/QN5ETZBfW/64Z+uzChao+\n" +
                "CVn3l+8CgYEA0R8A2rKj+BO4TY6qvE25RH5a/eRkW+VjCH9ibUcpoiXTH7u1fE1Z\n" +
                "i8Pvh0MQOagRVTjGlkhDCyBgCuzaslzedaQ1Ei/fsj/MMXS4Yp8Ar10H+Z8ijIM1\n" +
                "lvhnSh8MobtnVtHLL0NQiS3EQ3QdTQdnK3sv/zKy7iUMoiYJXkPqdPcCgYEAwI3w\n" +
                "o1s9lOWcypBHPSzxxsT78iMEIwxgNusyqiZlmdqevlPRWhupqXY/L2Rhss9280/D\n" +
                "MVL/KmVqxZqgwk6st4B8qJXF1t8Zr5gbO2+td9Q63uGdApMT94lled/N3zWeaT4h\n" +
                "o5pbUJcX/NYQdEtw9tft1g26o1JWRc7Jp8eOjy8CgYBqOyGn90+sWfgqDetVtPYE\n" +
                "gVuf1kHVHPXt/yf0802G/Uf+utA60OBIS8SGJd88KtCDRlA7T4IfSNcBNcjzCpJH\n" +
                "mJ8NGhy85APKq8xu7O7gJpZCjEB0uMKkapOt54/3KMgaJoDdBYkH5qPo0DeCRdx3\n" +
                "DiEXtp2GtHNye3gO1tlniQKBgHoLvWGdHDw7CRUvO6gfy6NECbkgzqd0WauVighk\n" +
                "y/MnqYRTVhd5/yClDUl5o58VdnyjqsGhrI/vtixHZOujuD+bFeg4/ivCPuKYU3Jp\n" +
                "x5ZzE1lfwQf3tyknkgUcgL2gm6ZzNhkfZg9/pTmhaM59Xr1mgZ4yF4EbWAlpF1Hb\n" +
                "35ULAoGBAMPmGeQB61/QWKRfKnZM+TNZv4w2VH+Z0lrywH8fb42nzWwfqsjQFZSD\n" +
                "8d3zLaOjd0oLt+cEm5T8WFh4XZfq+ijR+8ajRhCCP39b5XTQCyvLSeQnvtg1Ua16\n" +
                "z+MMdv0h+fuKUJvpunp+86MsbYgbkWtzfTu9hmipd/Eci9gF25n9"
    }
}
