package ren.imyan.image2latex.ui.mathpix

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.transition.TransitionManager
import com.ethanhua.skeleton.Skeleton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFade
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.*
import ren.imyan.base.ActivityCollector.currActivity
import ren.imyan.fragment.BaseFragment
import ren.imyan.image2latex.R
import ren.imyan.image2latex.core.App
import ren.imyan.image2latex.core.CropImage
import ren.imyan.image2latex.core.CropImageResult
import ren.imyan.image2latex.databinding.FragmentMathpixBinding
import ren.imyan.image2latex.ui.main.MainActivity
import ren.imyan.image2latex.util.saveImageToFile
import ren.imyan.image2latex.util.string

/**
 * @author EndureBlaze/炎忍 https://github.com/EndureBlaze
 * @data 2021-04-14 19:46
 * @website https://imyan.ren
 */
class MathpixFragment : BaseFragment<FragmentMathpixBinding, MathpixViewModel>() {

    private val shortAnimationDuration by lazy {
        context?.resources?.getInteger(android.R.integer.config_longAnimTime)?.toLong()!!
    }

    private val toolbarSize by lazy {
        (requireActivity() as MainActivity).binding.layoutToolbar.toolbar.y
    }

    private val fadeAnim by lazy {
        MaterialFade().apply {
            duration = shortAnimationDuration
        }
    }

    private val resultTextSkeletonScreen by lazy {
        Skeleton.bind(binding.resultText)
            .load(R.layout.layout_img_skeleton)
            .build()
    }

    private val resultLatexSkeletonScreen by lazy {
        Skeleton.bind(binding.resultLatex)
            .load(R.layout.layout_img_skeleton)
            .build()
    }

    override fun initViewModel(): MathpixViewModel =
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(currActivity.application)
        )[MathpixViewModel::class.java]

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMathpixBinding = FragmentMathpixBinding.inflate(inflater, container, false)

    override fun initView() {

        TransitionManager.beginDelayedTransition(binding.showResultTextCard, MaterialFade().apply {
            duration = shortAnimationDuration
        })

        TransitionManager.beginDelayedTransition(binding.showResultLatexCard, MaterialFade().apply {
            duration = shortAnimationDuration
        })

        TransitionManager.beginDelayedTransition(
            binding.showImageButtonLayout,
            MaterialFade().apply {
                duration = shortAnimationDuration
            })


        val cropImage =
            registerForActivityResult(CropImage()) {
                it.data?.data?.let { cropImageUri ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val bitmap = cropImageUri.getBitmap()
                        viewModel.bitmap.value = bitmap
                    }
                }
            }

        val selectImage = registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                showIsCropImageDialog(cropImage, it)
            }
        }

        binding.selectImageCard.setOnClickListener {
            selectImage.launch("image/*")
        }

        binding.removeImage.setOnClickListener {
            binding.toSelectImageCard()
            binding.hideResultCard()
        }

        binding.processImage.setOnClickListener {
            viewModel.getMathpixData()
            binding.showResultCard()
        }

        binding.showResultErrorCard.setOnClickListener {
            it.isVisible = false
            viewModel.getMathpixData()
            binding.showResultCard()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun loadData() {
        viewModel.bitmap.observe(this) {
            binding.apply {
                showImage.setImageBitmap(it)
//                if (it.height >= 500) {
//                    showImage.maxHeight = 500
//                }
                toShowImageCard()
            }
        }

        viewModel.mathpixData.data.observe(this) {
            binding.apply {
                resultText.gravity = Gravity.CENTER
                resultLatex.gravity = Gravity.CENTER

                resultText.text = it.text
                resultLatex.setText("$$${it.latexStyled}$$")

                resultLatexSkeletonScreen.hide()
                resultTextSkeletonScreen.hide()
            }
        }

        viewModel.mathpixData.state.observe(this) {
            binding.apply {
                showResultLatexCard.isVisible = false
                showResultTextCard.isVisible = false
                showResultErrorCard.isVisible = true

                resultError.gravity = Gravity.CENTER
                resultError.text = "$it\n${R.string.request.string()}"
            }
        }
    }

    private fun showIsCropImageDialog(
        cropImage: ActivityResultLauncher<CropImageResult>,
        uri: Uri
    ) {
        MaterialAlertDialogBuilder(currActivity)
            .setTitle(R.string.crop_dialog_title)
            .setNegativeButton(R.string.ok) { _, _ ->
                cropImage.launch(CropImageResult(uri))
            }
            .setNeutralButton(R.string.cancel) { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val bitmap = uri.getBitmap()
                    viewModel.bitmap.value = bitmap
                }
            }
            .create().show()
    }

    private suspend fun Uri.getBitmap(): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = runCatching {
            BitmapFactory.decodeStream(
                context?.contentResolver?.openInputStream(
                    this@getBitmap
                )
            )
        }
        return@withContext bitmap.getOrThrow()
    }

    private fun FragmentMathpixBinding.toShowImageCard() {

        TransitionManager.beginDelayedTransition(
            this.selectImageCard as ViewGroup,
            MaterialContainerTransform().apply {
                startView = this@toShowImageCard.selectImageCard
                endView = this@toShowImageCard.showImageCard
                interpolator = DecelerateInterpolator()
                addTarget(endView as MaterialCardView)
                setPathMotion(MaterialArcMotion())
                scrimColor = Color.TRANSPARENT
            })

        this.showImageCard.isVisible = true
        this.selectImageCard.isVisible = false

        TransitionManager.beginDelayedTransition(this.showImageButtonLayout, MaterialFadeThrough())
        this.showImageButtonLayout.visibility = View.VISIBLE
    }

    private fun FragmentMathpixBinding.toSelectImageCard() {
        TransitionManager.beginDelayedTransition(
            this.showImageCard as ViewGroup,
            MaterialContainerTransform().apply {
                startView = this@toSelectImageCard.showImageCard
                endView = this@toSelectImageCard.selectImageCard
                addTarget(endView as MaterialCardView)
                setPathMotion(MaterialArcMotion())
                scrimColor = Color.TRANSPARENT
            })
        this.selectImageCard.isVisible = true
        this.showImageCard.isVisible = false

        TransitionManager.beginDelayedTransition(this.showImageButtonLayout, MaterialFadeThrough())
        this.showImageButtonLayout.isVisible = false
    }

    @SuppressLint("SetTextI18n")
    private fun FragmentMathpixBinding.showResultCard() {

//        binding.resultText.text = """
//            \( \lim _{x \rightarrow 3}\left(\frac{x^{2}+9}{x-3}\right) \)
//        """.trimIndent()

//        binding.resultLatex.setText("$$\\lim _{x \\rightarrow 3}\\left(\\frac{x^{2}+9}{x-3}\\right)$$")

        showImageButtonLayout.isVisible = false

        showResultTextCard.isVisible = true

        TransitionManager.beginDelayedTransition(showResultLatexCard, MaterialFade().apply {
            duration = 500
        })
        showResultLatexCard.isVisible = true

        resultLatexSkeletonScreen.show()
        resultTextSkeletonScreen.show()
    }


    private fun FragmentMathpixBinding.hideResultCard() {


        TransitionManager.beginDelayedTransition(this.showResultTextCard, fadeAnim)
        showResultTextCard.isVisible = false

        TransitionManager.beginDelayedTransition(showResultLatexCard, fadeAnim)
        showResultLatexCard.isVisible = false
    }
}