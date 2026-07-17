package fansirsqi.xposed.sesame.model

fun <F : ModelField<*>> F.withDesc(desc: String?): F = apply {
    this.desc = desc
}
